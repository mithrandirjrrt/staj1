# Staj1: Advanced MediaPipe Landmark Detection Android App

This project is an Android application that performs real-time, on-device Face and Hand landmark detection using Google MediaPipe and CameraX.

The application processes a live camera feed and draws user-selectable, dynamic filters onto a custom `OverlayView`. It can also detect real-time states, such as "Mouth Open," by calculating the aspect ratios of facial landmarks.

<img width="2299" height="2299" alt="mediapipe_face_landmark_fullsize" src="https://github.com/user-attachments/assets/bf4d5d09-d60f-4162-b6b9-bc139199943d" />

## Core Features

* **Live Camera Feed:** A stable and modern camera preview using the `CameraX` library.
* **Dual Detection Modes:** Ability to switch between `FaceLandmarker` and `HandLandmarker` models.
* **Custom Drawing Layer:** A custom `OverlayView` that correctly draws incoming coordinates to the screen, correcting for rotation and front-camera mirroring.
* **Dynamic Face Filtering:** User-selectable filters include:
    * Eyes
    * Eyebrows
    * Nose
    * Cheeks
    * Lips (Full)
    * Inner Mouth (Teeth/Tongue)
    * Face Oval
* **Dynamic Hand Filtering:** User-selectable filters include:
    * Palm
    * Fingertips
* **Real-time State Detection:** Detects "Mouth Open" / "Closed" state by calculating the vertical and horizontal ratios of facial landmarks.

## Tech Stack Used

* **Kotlin:** Primary programming language.
* **Google MediaPipe (Tasks Vision):** For running `face_landmarker.task` and `hand_landmarker.task` models in `LIVE_STREAM` mode.
* **Android CameraX:** For managing the camera lifecycle and providing the `ImageAnalysis` stream.
* **Material Components:** For modern UI components like `ChipGroup` and `Chip`.
* **ViewBinding:** For type-safe access to XML components.

## How It Works

1.  `MainActivity` initializes CameraX and binds it to the `PreviewView`.
2.  The `ImageAnalysis` use case sends each frame to the active MediaPipe detector (`FaceLandmarker` or `HandLandmarker`) running in `LIVE_STREAM` mode.
3.  The detector's `ResultListener` receives the detected landmark coordinates.
4.  These results are passed to the `OverlayView`. The `OverlayView` knows which filters are active based on the user's `ChipGroup` selections (`faceFilters`, `handFilters` sets).
5.  `OverlayView` correctly scales the coordinates, accounting for device rotation (`isPortraitMode`) and camera direction (`isFrontCamera`), and draws boxes for *only* the selected filters (e.g., "Nose", "Palm") onto the canvas.

## Setup

1.  Clone this repository.
2.  Open the project in Android Studio.
3.  Wait for the necessary Gradle dependencies to download.
4.  Run the application on a physical Android device (or an emulator with a camera).
