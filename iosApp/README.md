# iosApp — shell de iOS

Contiene el código Swift del host. **No incluye el `.xcodeproj`**: crearlo es un paso de dos minutos
en Xcode y generar un `project.pbxproj` a mano, sin poder abrirlo para comprobarlo, produce un
archivo que hay que depurar en XML — peor que crearlo desde cero.

```
iosApp/iosApp/
├── iOSApp.swift      punto de entrada SwiftUI
├── ContentView.swift envoltorio del UIViewController de Compose
└── Info.plist        incluye NSCameraUsageDescription
```

## Crear el proyecto (una sola vez, en macOS)

1. **Xcode → File → New → Project → iOS → App.** Nombre `iosApp`, interfaz **SwiftUI**, lenguaje
   **Swift**. Guardarlo dentro de `iosApp/` de este repositorio, de modo que el `.xcodeproj` quede
   junto a la carpeta `iosApp/iosApp/` que ya existe.
2. Reemplazar los archivos generados por los tres de este repo (Xcode habrá creado sus propias
   versiones de `iOSApp.swift`, `ContentView.swift` e `Info.plist`).
3. **Build Phases → New Run Script Phase**, colocada **antes** de "Compile Sources":

   ```bash
   cd "$SRCROOT/.."
   ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
   ```

   En "Input Files" no hace falta nada; desmarcar *"Based on dependency analysis"* para que corra
   siempre.
4. **Build Settings → Framework Search Paths**, añadir en modo recursivo:

   ```
   $(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
   ```
5. **Build Settings → Other Linker Flags**: `-framework ComposeApp`.
6. Comprobar que `Info.plist` conserva `NSCameraUsageDescription`. **Sin esa clave iOS no deniega el
   permiso: mata la app** en cuanto `AVCaptureDevice` pide acceso.

Una vez creado, versionar el `.xcodeproj` y borrar esta sección.

## Qué le da Kotlin

`:composeApp` publica el framework `ComposeApp` (`isStatic = true`) para `iosArm64` e
`iosSimulatorArm64`, y expone `MainViewController()` en
`composeApp/src/iosMain/kotlin/com/whyscan/MainViewController.kt`. Esa función arranca Koin de
forma idempotente y devuelve el `UIViewController` con la UI compartida.

El motor de escaneo de iOS es [`:engines:vision-ios`](../engines/vision-ios): `AVCaptureSession` con
`AVCaptureMetadataOutput`, sin dependencias externas. Aporta también su superficie de preview
(`CameraPreviewEngine`, ver [ADR-0007](../docs/adr/ADR-0007-preview-como-capacidad-del-motor.md)).

## Estado

Ningún archivo Kotlin de iOS se ha compilado todavía: hace falta macOS. El `interop` con
AVFoundation, el `NSObject` que implementa `AVCaptureMetadataOutputObjectsDelegateProtocol` y la
subclase de `UIView` que redimensiona el `AVCaptureVideoPreviewLayer` son los puntos donde es más
probable que aparezcan errores de compilación en el primer intento.
