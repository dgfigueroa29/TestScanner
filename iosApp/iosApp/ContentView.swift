import SwiftUI
import UIKit
import ComposeApp

/// Envoltorio del `UIViewController` que expone `:composeApp`.
///
/// Es todo el código Swift del proyecto: igual que `MainActivity` en Android, este shell no
/// contiene lógica ni UI propia. Si algún día crece, es señal de que algo específico de iOS se
/// coló donde no debía.
struct ComposeView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        // `MainViewController()` arranca Koin de forma idempotente antes del primer composable.
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
    }
}
