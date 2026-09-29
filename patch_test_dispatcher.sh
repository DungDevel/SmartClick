awk '/mockNetwork = mockk\(\)/{print "        mockNetwork = mockk()\n"; next} 
     /orchestrator = DiscoveryOrchestrator\(/{print "        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)\n        orchestrator = DiscoveryOrchestrator(\n            brandProfileRepo,\n            wifiNetworkProvider,\n            probeFactory,\n            deviceIdentifier,\n            dispatcher\n        )"; in_ctor=1; next}
     in_ctor && /\)/{in_ctor=0; next}
     in_ctor {next}
     1' app/src/test/java/com/example/smartclick/app/tvconnection/discovery/DiscoveryOrchestratorTest.kt > temp2.kt
mv temp2.kt app/src/test/java/com/example/smartclick/app/tvconnection/discovery/DiscoveryOrchestratorTest.kt
