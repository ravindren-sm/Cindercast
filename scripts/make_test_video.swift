// Generates the local test stream used with scripts/fake_portal.py (--stream-file): a 30 s (or the length given as the second argument), 1280x720,
// 30 fps H.264 MP4, video only, a moving bar over a colour that changes every second. Invented content.
//   swiftc -O scripts/make_test_video.swift -o /tmp/make_test_video && /tmp/make_test_video test.mp4 150
// No ffmpeg needed: AVFoundation ships with macOS.
import AVFoundation
import CoreVideo

let out = URL(fileURLWithPath: CommandLine.arguments[1])
try? FileManager.default.removeItem(at: out)
// Optional second argument: length in seconds (default 30).
let seconds = CommandLine.arguments.count > 2 ? (Int(CommandLine.arguments[2]) ?? 30) : 30
let w = 1280, h = 720, fps = 30

let writer = try AVAssetWriter(outputURL: out, fileType: .mp4)
writer.shouldOptimizeForNetworkUse = true          // moov first, so it can start playing while downloading
let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
    AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: w, AVVideoHeightKey: h,
    AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 2_000_000, AVVideoMaxKeyFrameIntervalKey: fps],
])
let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA, kCVPixelBufferWidthKey as String: w, kCVPixelBufferHeightKey as String: h,
])
writer.add(input)
writer.startWriting()
writer.startSession(atSourceTime: .zero)

let palette: [(UInt8, UInt8, UInt8)] = [(200, 40, 40), (40, 160, 60), (40, 80, 200), (200, 160, 30), (150, 60, 180), (30, 160, 170)]
for frame in 0..<(fps * seconds) {
    while !input.isReadyForMoreMediaData { Thread.sleep(forTimeInterval: 0.002) }
    var buffer: CVPixelBuffer?
    CVPixelBufferPoolCreatePixelBuffer(nil, adaptor.pixelBufferPool!, &buffer)
    guard let pb = buffer else { fatalError("no buffer") }
    CVPixelBufferLockBaseAddress(pb, [])
    let base = CVPixelBufferGetBaseAddress(pb)!.assumingMemoryBound(to: UInt8.self)
    let stride = CVPixelBufferGetBytesPerRow(pb)
    let (r, g, b) = palette[(frame / fps) % palette.count]
    let barX = (frame * 16) % (w - 120)
    for y in 0..<h {
        for x in 0..<w {
            let i = y * stride + x * 4
            let inBar = x >= barX && x < barX + 120
            base[i] = inBar ? 255 : b; base[i + 1] = inBar ? 255 : g; base[i + 2] = inBar ? 255 : r; base[i + 3] = 255
        }
    }
    CVPixelBufferUnlockBaseAddress(pb, [])
    adaptor.append(pb, withPresentationTime: CMTime(value: CMTimeValue(frame), timescale: CMTimeScale(fps)))
}
input.markAsFinished()
let done = DispatchSemaphore(value: 0)
writer.finishWriting { done.signal() }
done.wait()
print("status:", writer.status.rawValue, writer.error as Any)
