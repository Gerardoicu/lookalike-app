# AI-001 local fixtures

This directory documents the local-only files used by the AI-001 facial similarity spike.

Keep downloaded model weights and face-image fixtures under `backend/src/test/resources/ai-001/local/`.
That directory is ignored by Git because the files may be large, third-party licensed, or personal/sensitive.

Expected local layout:

```text
backend/src/test/resources/ai-001/local/
  models/
    face_detection_yunet_2023mar.onnx
    face_recognition_sface_2021dec.onnx
  fixtures/
    same-person-1.jpg
    same-person-2.jpg
    different-person-1.jpg
    different-person-2.jpg
```

Run the spike by setting `AI001_ASSET_DIR` to the absolute path of the `local` directory.
The normal backend verification skips this spike when local assets are absent.
