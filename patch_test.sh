awk '/@Before/{print "@Before\n    fun setup() {\n        io.mockk.mockkStatic(android.util.Log::class)\n        io.mockk.every { android.util.Log.d(any(), any()) } returns 0\n        io.mockk.every { android.util.Log.w(any(), any<String>()) } returns 0"; next} 
     /fun setup\(\) \{/{next} 
     1' app/src/test/java/com/example/smartclick/app/tvconnection/discovery/DiscoveryOrchestratorTest.kt > temp.kt
mv temp.kt app/src/test/java/com/example/smartclick/app/tvconnection/discovery/DiscoveryOrchestratorTest.kt
