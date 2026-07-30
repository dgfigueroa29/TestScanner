# iosApp — shell de iOS

Este directorio alojará el proyecto Xcode que hospeda la UI compartida. Se crea en la **Fase 3**
del roadmap, porque generar y mantener un `.xcodeproj` requiere macOS y no aporta nada hasta que
exista el motor de Vision.

## Qué contendrá

- `iosApp.xcodeproj` — proyecto Xcode.
- `iosApp/ContentView.swift` — host SwiftUI que envuelve el `UIViewController` de Compose:

  ```swift
  import SwiftUI
  import ComposeApp

  struct ComposeView: UIViewControllerRepresentable {
      func makeUIViewController(context: Context) -> UIViewController {
          MainViewControllerKt.MainViewController()
      }
      func updateUIViewController(_ controller: UIViewController, context: Context) {}
  }
  ```

- `iosApp/Info.plist` con `NSCameraUsageDescription` — obligatorio: sin esa clave iOS mata la app
  al abrir la cámara.

## Enganche con Kotlin

`:composeApp` ya publica el framework `ComposeApp` (`isStatic = true`) para `iosX64`, `iosArm64` e
`iosSimulatorArm64`, y expone `MainViewController()` en
`composeApp/src/iosMain/kotlin/com/testscanner/MainViewController.kt`. El proyecto Xcode solo tiene
que enlazarlo mediante una *Run Script Phase* que invoque
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`.
