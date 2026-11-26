#!/usr/bin/env swift
// Builds the launcher icon and the Fire TV banner from the owner's logo artwork (branding/cindercast-logo-source.jpg, 2048 x 2048:
// flame, log and signal arcs above the word CINDERCAST on a dark field). Native macOS only (AppKit).
//
//   swift scripts/make_brand_assets.swift
//
// Writes branding/cindercast-icon-512.png (rounded, transparent corners), branding/cindercast-banner-1280x720.png and the
// two Android resources app/src/main/res/drawable-xhdpi/ic_launcher.png (256) and banner.png (640 x 360).
import AppKit

let root = FileManager.default.currentDirectoryPath
guard let src = NSImage(contentsOfFile: root + "/branding/cindercast-logo-source.jpg"),
      let cg = src.cgImage(forProposedRect: nil, context: nil, hints: nil) else { fatalError("source image not found") }

// The artwork without the word: flame, log and arcs (original pixel coordinates; the image is 2048 x 2048).
let art = CGRect(x: 414, y: 245, width: 1219, height: 1219)
guard let artImage = cg.cropping(to: art) else { fatalError("crop failed") }

// The background colour of the artwork, sampled just inside its left edge, halfway down so the banner and the picture match.
let rep = NSBitmapImageRep(cgImage: cg)
let bg = rep.colorAt(x: Int(art.minX) + 12, y: Int(art.midY)) ?? NSColor(calibratedRed: 0.05, green: 0.07, blue: 0.10, alpha: 1)

func png(_ size: CGSize, _ draw: (CGContext) -> Void) -> Data {
    let w = Int(size.width), h = Int(size.height)
    let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!,
                        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    ctx.interpolationQuality = .high
    draw(ctx)
    let img = ctx.makeImage()!
    return NSBitmapImageRep(cgImage: img).representation(using: .png, properties: [:])!
}
func write(_ data: Data, _ path: String) { try! data.write(to: URL(fileURLWithPath: root + "/" + path)) }

// Icon: the artwork on a rounded square with transparent corners.
func icon(_ side: CGFloat) -> Data { png(CGSize(width: side, height: side)) { c in
    let r = CGRect(x: 0, y: 0, width: side, height: side)
    c.addPath(CGPath(roundedRect: r, cornerWidth: side * 0.22, cornerHeight: side * 0.22, transform: nil)); c.clip()
    c.setFillColor(bg.cgColor); c.fill(r)
    c.draw(artImage, in: r)
} }
write(icon(512), "branding/cindercast-icon-512.png")
write(icon(256), "app/src/main/res/drawable-xhdpi/ic_launcher.png")

// Banner: the artwork on the left, the name and a line below it on the right.
func banner(_ w: CGFloat, _ h: CGFloat) -> Data { png(CGSize(width: w, height: h)) { c in
    let s = h / 720
    c.setFillColor(bg.cgColor); c.fill(CGRect(x: 0, y: 0, width: w, height: h))
    let side = 640 * s
    let box = CGRect(x: 60 * s, y: (h - side) / 2, width: side, height: side)
    c.draw(artImage, in: box)
    // Fade the picture's edges into the banner colour, so no square shows where it meets the background.
    let solid = bg.usingColorSpace(.sRGB)!.cgColor, clear = bg.usingColorSpace(.sRGB)!.withAlphaComponent(0).cgColor
    let fade = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB)!, colors: [solid, clear] as CFArray, locations: [0, 1])!
    let f = 90 * s
    c.drawLinearGradient(fade, start: CGPoint(x: box.minX, y: 0), end: CGPoint(x: box.minX + f, y: 0), options: [])
    c.drawLinearGradient(fade, start: CGPoint(x: box.maxX, y: 0), end: CGPoint(x: box.maxX - f, y: 0), options: [])
    c.drawLinearGradient(fade, start: CGPoint(x: 0, y: box.minY), end: CGPoint(x: 0, y: box.minY + f), options: [])
    c.drawLinearGradient(fade, start: CGPoint(x: 0, y: box.maxY), end: CGPoint(x: 0, y: box.maxY - f), options: [])
    let ns = NSGraphicsContext(cgContext: c, flipped: false)
    NSGraphicsContext.saveGraphicsState(); NSGraphicsContext.current = ns
    let cream = NSColor(calibratedRed: 1.0, green: 0.91, blue: 0.77, alpha: 1)
    let soft = NSColor(calibratedRed: 0.72, green: 0.68, blue: 0.64, alpha: 1)
    let title = NSAttributedString(string: "Cindercast", attributes: [.font: NSFont(name: "AvenirNext-DemiBold", size: 88 * s) ?? NSFont.boldSystemFont(ofSize: 88 * s), .foregroundColor: cream])
    let line = NSAttributedString(string: "Live TV, movies and shows", attributes: [.font: NSFont(name: "AvenirNext-Regular", size: 36 * s) ?? NSFont.systemFont(ofSize: 36 * s), .foregroundColor: soft])
    title.draw(at: CGPoint(x: 730 * s, y: h / 2 - 10 * s))
    line.draw(at: CGPoint(x: 734 * s, y: h / 2 - 70 * s))
    NSGraphicsContext.restoreGraphicsState()
} }
write(banner(1280, 720), "branding/cindercast-banner-1280x720.png")
write(banner(640, 360), "app/src/main/res/drawable-xhdpi/banner.png")
print("brand assets written")
