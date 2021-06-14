/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
    val database: SleepDatabaseDao,
    application: Application
) : AndroidViewModel(application) {

    //enable us to cancel coroutines
    private var viewModelJob = Job()

    //Dispatchers.MAIN launches the coroutine on the main thread
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private var tonight = MutableLiveData<SleepNight?>()
    val nights = database.getAllNights()
    val nightString = Transformations.map(nights) {
        formatNights(nights.value!!, application.resources)

    }
    private val _showSnackBar = MutableLiveData<Boolean>()
    val showSnackBar: LiveData<Boolean>
        get() = _showSnackBar

    fun doneShowingSnackBar() {
        _showSnackBar.value = false
    }

    val startButtonVisible = Transformations.map(tonight) {
        null == it
    }
    val stopButtonVisible = Transformations.map(tonight) {
        null != it
    }
    val clearButtonVisible = Transformations.map(nights) {
        it?.isNotEmpty()
    }

    private val _navigateToSleepQuality = MutableLiveData<SleepNight>()
    val navigateToSleepQuality: LiveData<SleepNight>
        get() = _navigateToSleepQuality

    fun doneNavigating() {
        _navigateToSleepQuality.value = null
    }

    init {
        initializeTonight()
    }

    private fun initializeTonight() {
        uiScope.launch {
            tonight.value = getTonightFromDatabase()
        }
    }

    //suspend enables us not to block the ui thread
    private suspend fun getTonightFromDatabase(): SleepNight? {
        //create another coroutine in IO context
        return withContext(Dispatchers.IO) {
            var night = database.getTonight()
            if (night?.endTimeMilli != night?.startTimeMilli) {
                night = null
            }
            night
        }

    }

    fun onStartTracking() {
        uiScope.launch {
            val newNight = SleepNight()
            insert(newNight)
            tonight.value = getTonightFromDatabase()
        }
    }

    private suspend fun insert(sleepNight: SleepNight) {
        return withContext(Dispatchers.IO) {
            database.insert(sleepNight)
        }
    }

    fun onStopTracking() {
        uiScope.launch {
            val oldNight = tonight.value ?: return@launch
            oldNight.endTimeMilli = System.currentTimeMillis()
            update(oldNight)
            //navigate
            _navigateToSleepQuality.value = oldNight
        }
    }

    private suspend fun update(sleepNight: SleepNight) {
        return withContext(Dispatchers.IO) {
            database.update(sleepNight)
        }
    }

    private val _navigateToSleepDataQuality = MutableLiveData<Long>()
    val navigateToSleepDataQuality: LiveData<Long>
        get() = _navigateToSleepDataQuality

    fun onSleepNightClicked(id: Long) {
        _navigateToSleepDataQuality.value = id
    }
    fun onSleepDataQualityNavigated(){
        _navigateToSleepDataQuality.value = null
    }

    fun onClear() {
        uiScope.launch {
            clear()
            tonight.value = null
            _showSnackBar.value = true
        }
    }

    private suspend fun clear() {
        return withContext(Dispatchers.IO) {
            database.clear()
        }
    }


    ///called everytime the viewModel is destroyed
    override fun onCleared() {
        super.onCleared()
        //cancel the Job so the coroutine will be stopped
        viewModelJob.cancel()
    }


}

