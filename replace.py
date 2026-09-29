import os
import re

replacements = {
    r'"Scanning for devices..."': 'stringResource(R.string.scanning_for_devices)',
    r'"Select Device"': 'stringResource(R.string.select_device)',
    r'"Delete"': 'stringResource(R.string.delete)',
    r'"Shot"': 'stringResource(R.string.shot)',
    r'"Config"': 'stringResource(R.string.config)',
    r'"Voice Search"': 'stringResource(R.string.voice_search)',
    r'"Send Text to TV"': 'stringResource(R.string.send_text_to_tv)',
    r'"Exit"': 'stringResource(R.string.exit)',
    r'"VOL"': 'stringResource(R.string.vol)',
    r'"CH"': 'stringResource(R.string.ch)',
    r'"Back"': 'stringResource(R.string.back)',
    r'"Refresh"': 'stringResource(R.string.refresh)',
    r'"Selected"': 'stringResource(R.string.selected)',
    r'"Power"': 'stringResource(R.string.power)',
    r'"Mute"': 'stringResource(R.string.mute)',
    r'"Lock"': 'stringResource(R.string.lock)',
    r'"Go"': 'stringResource(R.string.go)',
    r'"Email"': 'stringResource(R.string.email)',
    r'"Password"': 'stringResource(R.string.password)',
    r'"Log In"': 'stringResource(R.string.log_in)',
    r'"Continue with Google"': 'stringResource(R.string.continue_with_google)',
    r'"Account"': 'stringResource(R.string.account)',
    r'"Premium"': 'stringResource(R.string.premium)',
    r'"Free"': 'stringResource(R.string.free)',
    r'"Sign Out"': 'stringResource(R.string.sign_out)',
    r'"Not signed in"': 'stringResource(R.string.not_signed_in)',
    r'"Sign in to sync devices across phones"': 'stringResource(R.string.sign_in_to_sync)',
    r'"Sign In"': 'stringResource(R.string.sign_in)',
    r'"Create Account"': 'stringResource(R.string.create_account)',
    r'"Appearance"': 'stringResource(R.string.appearance)',
    r'"Haptic Feedback"': 'stringResource(R.string.haptic_feedback)',
    r'"Vibrate on tap"': 'stringResource(R.string.vibrate_on_tap)',
    r'"Provide physical feedback when pressing remote buttons."': 'stringResource(R.string.provide_physical_feedback)',
    r'"Privacy Policy"': 'stringResource(R.string.privacy_policy)',
    r'"Premium member"': 'stringResource(R.string.premium_member)',
    r'"Guest"': 'stringResource(R.string.guest)',
    r'"Sign in to backup devices"': 'stringResource(R.string.sign_in_to_backup)',
    r'"PRO"': 'stringResource(R.string.pro)',
    r'"Account Information"': 'stringResource(R.string.account_information)',
    r'"Language"': 'stringResource(R.string.language)',
    r'"Terms & Conditions"': 'stringResource(R.string.terms_and_conditions)',
    r'"Upgrade to Premium â\xad "': 'stringResource(R.string.upgrade_to_premium)',
    r'"Update Now"': 'stringResource(R.string.update_now)',
    r'"SmartClick Premium"': 'stringResource(R.string.smartclick_premium)',
    r'"Unlock all features"': 'stringResource(R.string.unlock_all_features)',
    r'"Start Free Trial"': 'stringResource(R.string.start_free_trial)',
    r'"Best Value"': 'stringResource(R.string.best_value)',
    r'"No TV Connected"': 'stringResource(R.string.no_tv_connected)',
    r'"Connect to your Smart TV to start using this feature."': 'stringResource(R.string.connect_to_tv)',
    r'"Try Again"': 'stringResource(R.string.try_again)'
}

def add_imports(content):
    imports = ["import androidx.compose.ui.res.stringResource", "import com.example.smartclick.R"]
    import_block_end = content.find("import ")
    if import_block_end == -1: return content
    
    # find where imports end
    last_import = content.rfind("import ")
    if last_import != -1:
        end_of_line = content.find("\n", last_import)
        
        has_string_res = "import androidx.compose.ui.res.stringResource" in content
        has_r = "import com.example.smartclick.R" in content
        
        insert_str = ""
        if not has_string_res: insert_str += "\nimport androidx.compose.ui.res.stringResource"
        if not has_r: insert_str += "\nimport com.example.smartclick.R"
        
        if insert_str:
            content = content[:end_of_line] + insert_str + content[end_of_line:]
    return content

directory = r"D:\SmartClick\app\src\main\java\com\example\smartclick"

for root, _, files in os.walk(directory):
    for file in files:
        if file.endswith(".kt"):
            filepath = os.path.join(root, file)
            with open(filepath, "r", encoding="utf-8") as f:
                content = f.read()

            new_content = content
            for old, new in replacements.items():
                new_content = re.sub(old, new, new_content)

            # Special cases
            new_content = new_content.replace('"Unlimited devices, channels, no ads"', 'stringResource(R.string.unlimited_devices)')
            new_content = new_content.replace('"Upgrade to Premium \u2b50 "', 'stringResource(R.string.upgrade_to_premium)')
            
            # AppNavigation tab aliases
            # AppTab.Remote -> AppTab(R.string.tab_remote, ...
            # we need to be careful with MainApp and AppTab
            
            if new_content != content:
                new_content = add_imports(new_content)
                with open(filepath, "w", encoding="utf-8") as f:
                    f.write(new_content)
                print(f"Updated {filepath}")
