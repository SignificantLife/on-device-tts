package com.mewmix.nabu.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mewmix.nabu.data.ModelState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GlobalRuntimeViewModelTest {

    @Test
    fun testInitialStateIsLoading() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = GlobalRuntimeViewModel(app)
        
        // Initial state should be Loading
        assertTrue(viewModel.modelState.value is ModelState.Loading)
    }
}
