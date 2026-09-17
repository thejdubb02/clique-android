# clique-android

Native Android client for CLIque. The prompt is a real `EditText` (`R.id.prompt_input` in `fragment_session.xml`). Never put input in the terminal WebView.

Spec: `SPEC.md`. Server API: `/root/platform/clique/API.md`.

Build: `ANDROID_HOME=/opt/android-sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug`.
