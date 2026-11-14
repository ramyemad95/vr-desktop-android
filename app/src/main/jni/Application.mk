# Application.mk for Moonlight

# Our minimum version is Android 5.0
APP_PLATFORM := android-21

# We support 16KB pages
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true

# Optimize the native code in debug builds too. VR Desktop is tested on phones with debug builds, and at -O0 the
# streaming core (packet reassembly, Reed-Solomon FEC, Opus) costs CPU time and heat on the thread that reads
# the network, which at 4K and 150 Mbps shows up as lost frames. Debug symbols are still generated.
APP_OPTIM := release
