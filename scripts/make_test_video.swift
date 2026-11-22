// Generates the local test stream used with scripts/fake_portal.py (--stream-file): a 1280x720, 30 fps H.264 MP4,
// a moving bar over a colour that changes every second, with two AAC audio tracks (English 440 Hz, Hindi 660 Hz,
// tagged with their languages) so audio switching can be tested. Invented content.
//
//   swiftc -O -swift-version 5 scripts/make_test_video.swift -o /tmp/make_test_video
//   /tmp/make_test_video test.mp4 150            # length in seconds (default 30)
//   /tmp/make_test_video test.mp4 150 noaudio    # video only
//
// No ffmpeg needed: AVFoundation ships with macOS.
import AVFoundation
import CoreVideo

let out = URL(fileURLWithPath: CommandLine.arguments[1])
let seconds = CommandLine.arguments.count > 2 ? (Int(CommandLine.arguments[2]) ?? 30) : 30
let withAudio = !(CommandLine.arguments.count > 3 && CommandLine.arguments[3] == "noaudio")
let w = 1280, h = 720, fps = 30
try? FileManager.default.removeItem(at: out)

/// A 16-bit mono 44.1 kHz sine tone as a WAV file, written by hand.
func writeTone(_ url: URL, hz: Double, seconds: Int) {
    let rate = 44100
    let n = rate * seconds
    var pcm = Data(count: n * 2)
    pcm.withUnsafeMutableBytes { raw in
        let p = raw.bindMemory(to: Int16.self)
        for i in 0..<n { p[i] = Int16(sin(2 * Double.pi * hz * Double(i) / Double(rate)) * 8000) }
    }
    var d = Data()
    func le32(_ v: UInt32) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 4)) }
    func le16(_ v: UInt16) { var x = v.littleEndian; d.append(Data(bytes: &x, count: 2)) }
    d.append("RIFF".data(using: .ascii)!); le32(UInt32(36 + pcm.count)); d.append("WAVE".data(using: .ascii)!)
    d.append("fmt ".data(using: .ascii)!); le32(16); le16(1); le16(1); le32(UInt32(rate)); le32(UInt32(rate * 2)); le16(2); le16(16)
    d.append("data".data(using: .ascii)!); le32(UInt32(pcm.count)); d.append(pcm)
    try! d.write(to: url)
}

let writer = try AVAssetWriter(outputURL: out, fileType: .mp4)
writer.shouldOptimizeForNetworkUse = true          // moov first, so it can start playing while downloading
let videoInput = AVAssetWriterInput(mediaType: .video, outputSettings: [
    AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: w, AVVideoHeightKey: h,
    AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 2_000_000, AVVideoMaxKeyFrameIntervalKey: fps],
])
let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: videoInput, sourcePixelBufferAttributes: [
    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA, kCVPixelBufferWidthKey as String: w, kCVPixelBufferHeightKey as String: h,
])
writer.add(videoInput)

struct AudioSource { let input: AVAssetWriterInput; let output: AVAssetReaderTrackOutput; let reader: AVAssetReader; let wav: URL }
var audio: [AudioSource] = []
if withAudio {
    for (code, tag, hz) in [("eng", "en", 440.0), ("hin", "hi", 660.0)] {
        let wav = FileManager.default.temporaryDirectory.appendingPathComponent("tone-\(code).wav")
        writeTone(wav, hz: hz, seconds: seconds)
        let asset = AVURLAsset(url: wav)
        let track = asset.tracks(withMediaType: .audio)[0]
        let reader = try AVAssetReader(asset: asset)
        let output = AVAssetReaderTrackOutput(track: track, outputSettings: [
            AVFormatIDKey: kAudioFormatLinearPCM, AVLinearPCMBitDepthKey: 16, AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsBigEndianKey: false, AVLinearPCMIsNonInterleaved: false,
        ])
        reader.add(output)
        let input = AVAssetWriterInput(mediaType: .audio, outputSettings: [
            AVFormatIDKey: kAudioFormatMPEG4AAC, AVNumberOfChannelsKey: 1, AVSampleRateKey: 44100, AVEncoderBitRateKey: 48000,
        ])
        input.languageCode = code
        input.extendedLanguageTag = tag
        writer.add(input)
        audio.append(AudioSource(input: input, output: output, reader: reader, wav: wav))
    }
}

writer.startWriting()
writer.startSession(atSourceTime: .zero)
for a in audio { a.reader.startReading() }

let palette: [(UInt8, UInt8, UInt8)] = [(200, 40, 40), (40, 160, 60), (40, 80, 200), (200, 160, 30), (150, 60, 180), (30, 160, 170)]
let totalFrames = fps * seconds
var frame = 0
let group = DispatchGroup()

// Each input is fed from its own queue; the writer interleaves them.
group.enter()
videoInput.requestMediaDataWhenReady(on: DispatchQueue(label: "video")) {
    while videoInput.isReadyForMoreMediaData {
        if frame >= totalFrames { videoInput.markAsFinished(); group.leave(); return }
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
        frame += 1
    }
}
for (n, a) in audio.enumerated() {
    group.enter()
    a.input.requestMediaDataWhenReady(on: DispatchQueue(label: "audio-\(n)")) {
        while a.input.isReadyForMoreMediaData {
            if let sample = a.output.copyNextSampleBuffer() { a.input.append(sample) }
            else { a.input.markAsFinished(); group.leave(); return }
        }
    }
}
group.wait()

let done = DispatchSemaphore(value: 0)
writer.finishWriting { done.signal() }
done.wait()
for a in audio { try? FileManager.default.removeItem(at: a.wav) }
print("status:", writer.status.rawValue, writer.error as Any)
