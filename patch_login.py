with open("app/src/main/java/com/example/smartclick/app/features/auth/LoginScreen.kt", "r", encoding="utf-8") as f:
    text = f.read()

text = text.replace('"Skip"', 'stringResource(R.string.skip)')
text = text.replace('"Welcome Back"', 'stringResource(R.string.login_welcome_back)')
text = text.replace('"Log in to sync your saved devices"', 'stringResource(R.string.sign_in_to_sync)')
text = text.replace('"Forgot password?"', 'stringResource(R.string.login_forgot_password)')
text = text.replace('"OR"', 'stringResource(R.string.login_or)')
text = text.replace('"Don\'t have an account? "', 'stringResource(R.string.login_dont_have_account) + " "')
text = text.replace('"Sign Up"', 'stringResource(R.string.sign_up)')

with open("app/src/main/java/com/example/smartclick/app/features/auth/LoginScreen.kt", "w", encoding="utf-8") as f:
    f.write(text)
