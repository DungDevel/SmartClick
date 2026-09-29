import re

path = r"D:\SmartClick\app\src\main\java\com\example\smartclick\app\features\onboarding\NotFoundScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    c = f.read()

c = c.replace('stringResource(R.string.connect_to_tv)', 'stringResource(R.string.cannot_find_tvs_network)') 
c = c.replace('text = stringResource(R.string.try_again),\n                    fontWeight = FontWeight.SemiBold,', 'text = stringResource(R.string.enter_ip_manually),\n                    fontWeight = FontWeight.SemiBold,')
c = c.replace('text = stringResource(R.string.guest),\n                    color = colors.tx2,', 'text = stringResource(R.string.skip_for_now),\n                    color = colors.tx2,')

with open(path, "w", encoding="utf-8") as f:
    f.write(c)

path = r"D:\SmartClick\app\src\main\java\com\example\smartclick\app\features\onboarding\DeviceFoundScreen.kt"
with open(path, "r", encoding="utf-8") as f:
    c = f.read()
    
c = c.replace('stringResource(R.string.scanning_network)', 'stringResource(R.string.searching_nearby_tvs)')
c = c.replace('stringResource(R.string.tv_not_found_try_manually)', 'stringResource(R.string.make_sure_tv_on)')

with open(path, "w", encoding="utf-8") as f:
    f.write(c)
