// Regenerates the dragon layer of Ghidra.icon from the Ghidra logo.
//
// Usage: swift make_icon_artwork.swift <GHIDRA_2.png> <output.png>
//
// The layer shows the dragon's head and right-hand loop.  The square cut-out is
// chosen so the body crossing to the other loop leaves the icon at its left edge,
// where the icon shape clips it.

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let canvasSize = 1024

// Square region of the logo, in logo pixels (y down); it may extend past the logo.
let cropX = 745.0
let cropY = -50.0
let cropSide = 880.0

let args = CommandLine.arguments
guard args.count == 3 else {
	FileHandle.standardError.write("usage: make_icon_artwork.swift <GHIDRA_2.png> <output.png>\n".data(using: .utf8)!)
	exit(2)
}

guard let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: args[1]) as CFURL, nil),
	let logo = CGImageSourceCreateImageAtIndex(source, 0, nil)
else {
	FileHandle.standardError.write("cannot read \(args[1])\n".data(using: .utf8)!)
	exit(1)
}

guard let context = CGContext(
	data: nil, width: canvasSize, height: canvasSize, bitsPerComponent: 8, bytesPerRow: 0,
	space: CGColorSpace(name: CGColorSpace.sRGB)!,
	bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
else {
	exit(1)
}
context.interpolationQuality = .high

let scale = Double(canvasSize) / cropSide
let width = Double(logo.width) * scale
let height = Double(logo.height) * scale
// Core Graphics puts the origin at the bottom left
let x = -cropX * scale
let y = Double(canvasSize) - (-cropY * scale) - height
context.draw(logo, in: CGRect(x: x, y: y, width: width, height: height))

guard let result = context.makeImage(),
	let destination = CGImageDestinationCreateWithURL(
		URL(fileURLWithPath: args[2]) as CFURL, UTType.png.identifier as CFString, 1, nil)
else {
	exit(1)
}
CGImageDestinationAddImage(destination, result, nil)
if !CGImageDestinationFinalize(destination) {
	FileHandle.standardError.write("cannot write \(args[2])\n".data(using: .utf8)!)
	exit(1)
}
