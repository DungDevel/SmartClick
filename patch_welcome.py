with open("app/src/main/java/com/example/smartclick/app/features/onboarding/WelcomeScreen.kt", "r", encoding="utf-8") as f:
    text = f.read()

text = text.replace('"Control Your TV\nEffortlessly"', 'stringResource(R.string.welcome_title)')

with open("app/src/main/java/com/example/smartclick/app/features/onboarding/WelcomeScreen.kt", "w", encoding="utf-8") as f:
    f.write(text)
