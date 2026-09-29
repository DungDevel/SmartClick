awk '/private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO/{next}
     /class DiscoveryOrchestrator @Inject constructor/{print; in_ctor=1; next}
     in_ctor && /deviceIdentifier: DeviceIdentifier,/{print "    private val deviceIdentifier: DeviceIdentifier"; next}
     in_ctor && /\){/{print; in_ctor=0; next}
     /val discoverJob = launch\(ioDispatcher\)/{print "        val discoverJob = launch(Dispatchers.IO) {"; next}
     1' app/src/main/java/com/example/smartclick/app/tvconnection/discovery/DiscoveryOrchestrator.kt > temp3.kt
mv temp3.kt app/src/main/java/com/example/smartclick/app/tvconnection/discovery/DiscoveryOrchestrator.kt
