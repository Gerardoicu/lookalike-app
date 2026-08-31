# AI-001 Facial Similarity Engine Validation

## Scope

AI-001 validates whether Lookalike can use a commercially usable facial similarity engine inside the Spring Boot backend.
This is a local backend spike only. It does not add upload behavior, a public API, persistence, authentication, a frontend workflow, model training, deployment architecture, rate limiting, CAPTCHA, cooldowns, advertising, or any future business behavior.

## Candidate decision

Primary practical JVM/server packaging candidate:

- Package: `com.microsoft.onnxruntime:onnxruntime:1.29.0`
- Role: Java CPU inference runtime for SFace ONNX representation extraction.
- Maven metadata: Sonatype Central identifies this artifact as `onnx-runtime`, packaging `jar`, and license `MIT License`.
- Repository metadata: ONNX Runtime documents Java bindings for running ONNX models on a JVM, with Java 8 or newer support.
- Commercial-use conclusion: compatible for commercial evaluation and distribution if MIT copyright and license notices are retained.

Supporting OpenCV package for detection/alignment in the spike:

- Package: `org.openpnp:opencv:4.9.0-0`
- Role: practical Java server packaging for OpenCV Java bindings and native libraries.
- Maven metadata: Sonatype Central identifies this artifact as `OpenPnP OpenCV`, packaging `bundle`, description `OpenCV packaged with native libraries and loader for multiple platforms`, and license `BSD License`.
- Repository metadata: the OpenPnP repository describes the package as OpenCV Java bindings packaged with native libraries and documents `nu.pattern.OpenCV.loadLocally()` for Java 12+.
- Commercial-use conclusion: compatible for commercial evaluation and distribution if BSD redistribution obligations are met, including retaining notices in source distributions, reproducing notices in binary distribution documentation/materials, and not using copyright holder or contributor names for endorsement without permission.

Official OpenCV package compatibility check:

- Package: `org.opencv:opencv:5.0.0.1`
- Role: official package compatibility reference only, not the practical Spring Boot candidate for this spike.
- Maven metadata: Sonatype Central identifies packaging as `aar`, with license `Apache-2.0`.
- Conclusion: the AAR packaging is Android-oriented and is not recommended as the backend JVM/server packaging path for this spike.

OpenCV runtime:

- OpenCV 4.9 is covered by OpenCV's current license statement that OpenCV 4.5.0 and higher use Apache License 2.0.
- Commercial-use conclusion: compatible for commercial evaluation and distribution if Apache 2.0 redistribution, notice, patent, and trademark obligations are met.

## Model weights

Face detection:

- Source: OpenCV Zoo, `models/face_detection_yunet/face_detection_yunet_2023mar.onnx`
- Reason: YuNet model version compatible with OpenCV 4.x.
- License: MIT License in the model directory.
- Commercial-use conclusion: compatible for commercial evaluation and distribution if the MIT copyright and permission notice are included with copies or substantial portions.

Face representation/comparison:

- Source: OpenCV Zoo, `models/face_recognition_sface/face_recognition_sface_2021dec.onnx`
- Published model license: Apache License 2.0 in the OpenCV Zoo SFace model directory.
- Commercial-use conclusion: Apache 2.0 is commercially permissive for use and distribution if Apache 2.0 obligations are met.

Model weights are not committed to Git.

OpenCV Zoo model-license metadata does not fully resolve training-data provenance for the SFace 2021dec pretrained weight. OpenCV Zoo issue #313 is open and currently requests clarification for this exact model file, including whether the directory Apache 2.0 license explicitly covers the pretrained weight, which dataset produced it, and whether the relevant dataset/model terms permit commercial inference.

Project decision for AI-001:

- SFace 2021dec is accepted only as the provisional technical facial-embedding baseline for subsequent development.
- SFace 2021dec is not commercially cleared for monetized production because its exact training-data provenance remains unresolved.
- Commercial model clearance is a separate mandatory release blocker before any monetized public production launch.

## Reproducible local setup

Create a local ignored asset directory:

```powershell
New-Item -ItemType Directory -Force backend\src\test\resources\ai-001\local\models
New-Item -ItemType Directory -Force backend\src\test\resources\ai-001\local\fixtures
```

Download model weights from the official OpenCV Zoo raw URLs:

```powershell
Invoke-WebRequest -Uri "https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx" -OutFile "backend\src\test\resources\ai-001\local\models\face_detection_yunet_2023mar.onnx"
Invoke-WebRequest -Uri "https://github.com/opencv/opencv_zoo/raw/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx" -OutFile "backend\src\test\resources\ai-001\local\models\face_recognition_sface_2021dec.onnx"
```

Add local fixture images:

```text
backend/src/test/resources/ai-001/local/fixtures/same-person-1.jpg
backend/src/test/resources/ai-001/local/fixtures/same-person-2.jpg
backend/src/test/resources/ai-001/local/fixtures/different-person-1.jpg
backend/src/test/resources/ai-001/local/fixtures/different-person-2.jpg
```

The workstation validation used:

- `same-person-1.jpg`: `President Barack Obama.jpg` from Wikimedia Commons.
- `same-person-2.jpg`: `Obama Portrait 2006.jpg` from Wikimedia Commons.
- `different-person-1.jpg`: `Joe Biden presidential portrait.jpg` from Wikimedia Commons.
- `different-person-2.jpg`: `Donald Trump official portrait.jpg` from Wikimedia Commons.

Run the spike:

```powershell
$env:AI001_ASSET_DIR = (Resolve-Path backend\src\test\resources\ai-001\local).Path
cd backend
.\mvnw.cmd -Dtest=FacialSimilarityEngineSpikeTests test
```

The JUnit spike is skipped when `AI001_ASSET_DIR` is not set or local files are missing, so CI can continue without personal fixtures or model binaries.

## Local evidence

Local evidence must include:

- Two different photos of the same person.
- Photos of different people.
- Numeric same-person and different-person cosine results.
- Numeric same-person and different-person L2 results.
- CPU latency.
- JVM heap memory observation.
- Artifact and model sizes.
- Hardware and OS notes.

Results from this workstation:

```text
OS: Microsoft Windows 10 Pro 10.0.19045, 64-bit
CPU: AMD Ryzen 5 2400G with Radeon Vega Graphics, 4 cores, 8 logical processors
Memory: 23,000,364 KB visible physical memory
Java: OpenJDK 25.0.4 LTS, Microsoft build 25.0.4+7-LTS

OpenPnP OpenCV jar: 109,619,828 bytes
ONNX Runtime jar: 54,400,660 bytes
YuNet model: 232,589 bytes
SFace model: 38,696,353 bytes

Detected/aligned one face in each fixture image.
OpenCV DNN feature shape for each fixture: 1x128, CV_32F

OpenCV DNN recheck preprocessing:
aligned input: 112x112
scale factor: 1
mean: 0
swap R/B channels: true
normalization before inference: none

OpenCV DNN same-person cosine: 1.000000
OpenCV DNN same-person L2: 0.000000
OpenCV DNN different-person cosine #1: 1.000000
OpenCV DNN different-person cosine #2: 1.000000
OpenCV DNN different-person L2: 0.000000

OpenCV DNN result:
The OpenPnP/OpenCV DNN path is rejected for representation extraction because it produced constant vectors even after the preprocessing recheck.

ONNX Runtime embedding sample same1:
{0=0.05730045, 1=1.1056697, 2=0.06519708, 3=0.62566155, 4=0.6925205, 5=1.4768767, 6=0.8060161, 7=-1.8423947}

ONNX Runtime embedding sample same2:
{0=-0.123598136, 1=0.71739537, 2=-0.13669392, 3=-0.46984056, 4=0.7208334, 5=1.4834775, 6=-0.29467604, 7=-0.67744255}

ONNX Runtime embedding sample different1:
{0=-2.2747948, 1=-0.5622273, 2=1.2023426, 3=1.0571907, 4=-1.001504, 5=1.0177754, 6=-1.3117695, 7=-1.2617615}

ONNX Runtime embedding sample different2:
{0=0.36084595, 1=-0.08092426, 2=-0.7590953, 3=0.7097626, 4=-0.9742657, 5=-0.60147667, 6=-1.0160854, 7=0.5373705}

ONNX Runtime same-person cosine: 0.586089
ONNX Runtime same-person L2: 0.909847
ONNX Runtime different-person cosine #1: 0.144537
ONNX Runtime different-person cosine #2: -0.014183
ONNX Runtime different-person cosine #3: 0.096555
ONNX Runtime different-person cosine #4: 0.047557
ONNX Runtime different-person L2 #1: 1.308023
ONNX Runtime different-person L2 #2: 1.424207

cold runtime/model load: 4,333 ms
warm decode average/range per image: 213.56 ms [171-334]
warm YuNet detection/alignment average/range per image: 22.01 ms [15-57]
warm ONNX Runtime SFace embedding average/range per image: 42.04 ms [31-77]
warm comparison batch average/range for eight comparisons: 0.19 ms [0-0]
warm per-image total average/range: 277.62 ms [230-420]

memory before loading runtimes/models: heap 41 MB, non-heap 13 MB, committed virtual 465 MB
memory after OpenCV load: heap 45 MB, non-heap 14 MB, committed virtual 549 MB
memory after model load: heap 45 MB, non-heap 14 MB, committed virtual 590 MB
observed spike peak: heap 45 MB, non-heap 17 MB, committed virtual 1,402 MB

Result: the OpenPnP OpenCV 4.9.0-0 package loaded and executed on Java 25, YuNet detected faces, and OpenCV SFace alignment produced valid crops. OpenCV DNN representation extraction still produced indistinguishable vectors after matching SFace preprocessing. ONNX Runtime executed the same SFace ONNX model independently and produced non-constant embeddings that separated the same-person example from multiple different-person examples.

Java 25 native-access warning:
nu.pattern.OpenCV.loadLocally() calls System.load from an unnamed module. Java warned that --enable-native-access=ALL-UNNAMED avoids the warning and that restricted methods may be blocked in a future release unless native access is enabled.

ONNX Runtime model warning:
ONNX Runtime emitted graph warnings that SFace initializers also appear as graph inputs and may not be treated as constant weights, which can limit graph optimizations. Inference still completed and produced useful numeric separation.
```

## Recommendation

Recommended MVP pipeline:

```text
YuNet 2023mar + OpenPnP/OpenCV 4.9 for face detection/alignment
+
ONNX Runtime Java 1.29.0 + SFace 2021dec for representation extraction/comparison
```

The ONNX Runtime path met the AI-001 technical validation bar: it runs locally in the Spring Boot backend test JVM, uses commercially permissive runtime licenses, produces non-constant embeddings, and separates the same-person example from multiple different-person pairs well enough for an entertainment-oriented similarity workflow.

This is not a commercial-production clearance. The runtime packages are commercially compatible subject to their license obligations, but SFace 2021dec remains commercially unresolved because the exact pretrained-weight training-data provenance is not documented. The model may be used as a provisional technical baseline only until legal/compliance review or a later approved ticket clears a production model.

Preliminary production resource expectation for the MVP:

- CPU: budget at least 1 dedicated modern CPU core per active image-processing request lane; more cores will be needed for concurrent uploads or gallery scans.
- RAM: budget at least 1 GB available process/container memory for the MVP service path to cover JVM baseline, OpenCV native loading, ONNX Runtime native loading, model sessions, image buffers, and request overhead. The local spike observed only modest Java heap use but up to about 1.4 GB committed virtual memory.
- Startup: expect several seconds of cold runtime/model loading unless models and sessions are initialized at application startup.
- Warm processing: expect roughly 230-420 ms per image on this older 4-core workstation for decode, detection/alignment, and embedding; comparisons are effectively negligible for small candidate sets.

Production adoption still needs a later approved ticket for API design, asset management, privacy/security controls, threshold calibration on a broader licensed fixture set, native runtime packaging strategy, and final legal/compliance review of SFace training-data provenance before monetized public launch.

## Planning rejections

AI-001 stops further model research with the current evidence. The following alternatives were investigated during planning and rejected without implementation or benchmarking:

- ArcFace LResNet100E-IR / `arcfaceresnet100-8.onnx`: rejected because the ONNX Model Zoo documentation identifies its training source as Refined MS-Celeb-1M. That provenance does not provide the required commercial clarity for this ticket.
- AuraFace v1: rejected because, although the public model information describes Apache 2.0 weights and commercial intent, the exact training dataset names and authoritative dataset terms were not publicly documented closely enough to satisfy AI-001 commercial-provenance screening.

No additional model or runtime is introduced under AI-001.

## Sources

- OpenPnP Maven metadata: https://central.sonatype.com/artifact/org.openpnp/opencv
- OpenPnP repository: https://github.com/openpnp/opencv
- OpenPnP license: https://raw.githubusercontent.com/openpnp/opencv/master/LICENSE
- ONNX Runtime Maven metadata: https://central.sonatype.com/artifact/com.microsoft.onnxruntime/onnxruntime
- ONNX Runtime Java documentation: https://onnxruntime.ai/docs/get-started/with-java.html
- ONNX Runtime license: https://github.com/microsoft/onnxruntime/blob/main/LICENSE
- Official OpenCV Maven metadata: https://central.sonatype.com/artifact/org.opencv/opencv
- OpenCV license: https://opencv.org/license/
- OpenCV Zoo SFace provenance issue #313: https://github.com/opencv/opencv_zoo/issues/313
- YuNet 2023mar model: https://github.com/opencv/opencv_zoo/blob/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx
- YuNet license: https://raw.githubusercontent.com/opencv/opencv_zoo/main/models/face_detection_yunet/LICENSE
- SFace 2021dec model: https://github.com/opencv/opencv_zoo/blob/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx
- SFace license: https://raw.githubusercontent.com/opencv/opencv_zoo/main/models/face_recognition_sface/LICENSE
- Wikimedia Commons `President Barack Obama.jpg`: https://commons.wikimedia.org/wiki/File:President_Barack_Obama.jpg
- Wikimedia Commons `Obama Portrait 2006.jpg`: https://commons.wikimedia.org/wiki/File:Obama_Portrait_2006.jpg
- Wikimedia Commons `Joe Biden presidential portrait.jpg`: https://commons.wikimedia.org/wiki/File:Joe_Biden_presidential_portrait.jpg
- Wikimedia Commons `Donald Trump official portrait.jpg`: https://commons.wikimedia.org/wiki/File:Donald_Trump_official_portrait.jpg
