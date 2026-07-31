import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // La UI de Compose gestiona sus propios insets; sin esto SwiftUI le recorta la
                // zona segura y el preview de cámara deja de ocupar la pantalla completa.
                .ignoresSafeArea(.all)
        }
    }
}
