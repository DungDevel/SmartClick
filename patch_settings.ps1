$account = Get-Content -Raw "app/src/main/java/com/example/smartclick/app/features/settings/AccountInformationScreen.kt"
$account = $account -replace 'user.displayName\?', 'user.name'
$account = $account -replace 'user.displayName', 'user.name'
$account = $account -replace 'user.email \?: ""', '"user@example.com"'

Set-Content -Path "app/src/main/java/com/example/smartclick/app/features/settings/AccountInformationScreen.kt" -Value $account -Encoding UTF8

$main = Get-Content -Raw "app/src/main/java/com/example/smartclick/app/features/settings/SettingsMainScreen.kt"
$main = $main -replace 'user.displayName\?', 'user.name'
$main = $main -replace 'user.displayName', 'user.name'
$main = $main -replace 'Text\(user.email \?: "", color = colors.tx2, fontSize = 13.sp\)', 'Text("user@example.com", color = colors.tx2, fontSize = 13.sp)'

Set-Content -Path "app/src/main/java/com/example/smartclick/app/features/settings/SettingsMainScreen.kt" -Value $main -Encoding UTF8

$vm = Get-Content -Raw "app/src/main/java/com/example/smartclick/app/features/settings/SettingsViewModel.kt"
$vm = $vm -replace 'subscriptionRepository.subscribe\(plans.first\(\)\)', 'subscriptionRepository.subscribe(com.example.smartclick.app.domain.model.SubscriptionPlan.PREMIUM)'

Set-Content -Path "app/src/main/java/com/example/smartclick/app/features/settings/SettingsViewModel.kt" -Value $vm -Encoding UTF8
