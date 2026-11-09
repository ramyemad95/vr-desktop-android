# VR renderer design

1. **Decode:** MediaCodec configured with `KEY_LOW_LATENCY=1` (API 30+), outputting to a `Surface` built from a `SurfaceTexture`.
2. **Texture:** each `onFrameAvailable` → `updateTexImage()` on the GL thread. Frames stay on the GPU (zero copy).
3. **Scene:** a single quad, about 2.5 m wide and 2 m in front of the user, sampled with `samplerExternalOES`.
4. **Stereo:** Cardboard SDK `CardboardHeadTracker` for the pose and `CardboardLensDistortion` for per-eye
   view/projection matrices. Render each eye to the distortion framebuffer, then call `CardboardDistortionRenderer_renderEyeToDisplay`.
5. **Readability:** supersample the eye buffers by 1.2–1.5×, use mipmaps or bilinear filtering on the screen texture, and let the user resize the screen.
6. **Integration point in Moonlight:** replace the `SurfaceView` that `MediaCodecDecoderRenderer` draws into with our GL `Surface`.
