package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.findNavController
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.data.local.LocalDB
import com.udacity.project4.locationreminders.data.local.RemindersLocalRepository
import com.udacity.project4.locationreminders.geofence.GeofenceBroadcastReceiver
import com.udacity.project4.locationreminders.geofence.GeofencingConstants
import com.udacity.project4.locationreminders.geofence.createChannel
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject

class SaveReminderFragment : BaseFragment() {
    //Get the view model this time as a single to be shared with the another fragment
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var binding: FragmentSaveReminderBinding

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(requireContext(), GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(requireContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_save_reminder, container, false)
        setDisplayHomeAsUpEnabled(true)
        geofencingClient = LocationServices.getGeofencingClient(requireActivity())
        createChannel(requireContext())
        binding.viewModel = _viewModel

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = this
        binding.selectLocation.setOnClickListener {
            //            Navigate to another fragment to get the user location
            _viewModel.navigationCommand.value =
                NavigationCommand.To(SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment())
        }

        binding.saveReminder.setOnClickListener {
            val title = _viewModel.reminderTitle.value
            val description = _viewModel.reminderDescription.value
            val location = _viewModel.reminderSelectedLocationStr.value
            val latitude = _viewModel.latitude.value
            val longitude = _viewModel.longitude.value


//            TODO: use the user entered reminder details to:
//             1) add a geofencing request

            if (_viewModel.geofenceIsActive()) return@setOnClickListener
            val currentGeofenceIndex = _viewModel.nextGeofenceIndex()
            if(currentGeofenceIndex >= GeofencingConstants.NUM_LANDMARKS) {
               // removeGeofences()
                _viewModel.geofenceActivated()
                return@setOnClickListener
            }
            Log.d(TAG, "onViewCreated: $latitude and $longitude")
            val currentGeofenceData = GeofencingConstants.LANDMARK_DATA[currentGeofenceIndex]
            val geofence = Geofence.Builder()
                .setRequestId(_viewModel.selectedPOI.value?.placeId)
                .setCircularRegion(
                    latitude!!,
                    longitude!!,
                    GeofencingConstants.GEOFENCE_RADIUS_IN_METERS
                )
                .setExpirationDuration(GeofencingConstants.GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
            val geofencingRequest = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()
            geofencingClient.removeGeofences(geofencePendingIntent)?.run {
                addOnCompleteListener {
                    if (context?.let { it1 ->
                            ActivityCompat.checkSelfPermission(
                                it1,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        } != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return@addOnCompleteListener
                    }
                    geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)?.run {
                        addOnSuccessListener {
                            Toast.makeText(
                                context, "Geofence Added",
                                Toast.LENGTH_SHORT
                            )
                                .show()


                        }
                        addOnFailureListener {
                            Toast.makeText(
                                context, "Geofence Not Addedd",
                                Toast.LENGTH_SHORT
                            ).show()
                            if ((it.message != null)) {

                            }
                        }
                    }
                }
            }
//             2) save the reminder to the local db
            val reminder = _viewModel.selectedPOI.value?.let { it1 ->
                ReminderDataItem(
                    title.toString(), description.toString(), location, latitude, longitude,
                    it1.placeId
                )
            }
            if (reminder != null) {
                _viewModel.saveReminder(reminder)
            }
            view?.findNavController()
                ?.navigate(R.id.action_saveReminderFragment_to_reminderListFragment)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //make sure to clear the view model after destroy, as it's a single view model.
        _viewModel.onClear()
    }
}
