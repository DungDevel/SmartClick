awk '/val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher/{next}
     /dispatcher/{next}
     1' app/src/test/java/com/example/smartclick/app/tvconnection/discovery/DiscoveryOrchestratorTest.kt > temp4.kt
mv temp4.kt app/src/test/java/com/example/smartclick/app/tvconnection/discovery/DiscoveryOrchestratorTest.kt
