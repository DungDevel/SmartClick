#!/bin/bash
# Remove Provider<LgWebOsController> from LgWebOsDriver
sed -i 's/private val controllerProvider: Provider<LgWebOsController>//' D:/SmartClick/app/src/main/java/com/example/smartclick/app/tvconnection/lg/LgWebOsDriver.kt
sed -i 's/class LgWebOsDriver @Inject constructor(/class LgWebOsDriver @Inject constructor() : IProtocolDriver {/g' D:/SmartClick/app/src/main/java/com/example/smartclick/app/tvconnection/lg/LgWebOsDriver.kt
sed -i 's/) : IProtocolDriver {//g' D:/SmartClick/app/src/main/java/com/example/smartclick/app/tvconnection/lg/LgWebOsDriver.kt

# But since I already overwrote LgWebOsDriver.kt using Write, I should check its current state first!
cat D:/SmartClick/app/src/main/java/com/example/smartclick/app/tvconnection/lg/LgWebOsDriver.kt | grep -n "constructor"
