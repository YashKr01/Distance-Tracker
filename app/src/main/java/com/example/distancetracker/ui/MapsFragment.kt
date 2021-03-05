package com.example.distancetracker.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import androidx.fragment.app.Fragment

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import com.example.distancetracker.R
import com.example.distancetracker.databinding.FragmentMapsBinding
import com.example.distancetracker.service.TrackerService
import com.example.distancetracker.util.Constants.ACTION_SERVICE_START
import com.example.distancetracker.util.Constants.ACTION_SERVICE_STOP
import com.example.distancetracker.util.ExtensionFunctions.disable
import com.example.distancetracker.util.ExtensionFunctions.enable
import com.example.distancetracker.util.ExtensionFunctions.hide
import com.example.distancetracker.util.ExtensionFunctions.show
import com.example.distancetracker.util.MapUtil
import com.example.distancetracker.util.MapUtil.setCameraPosition
import com.example.distancetracker.util.Permissions.hasBackgroundLocationPermission
import com.example.distancetracker.util.Permissions.requestBackgroundLocationPermission
import com.google.android.gms.maps.CameraUpdateFactory

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.ButtCap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.vmadalin.easypermissions.EasyPermissions
import com.vmadalin.easypermissions.dialogs.SettingsDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MapsFragment : Fragment(), OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener,
    EasyPermissions.PermissionCallbacks {

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!

    private var locationList = mutableListOf<LatLng>()

    private lateinit var map: GoogleMap

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)

        binding.startButton.setOnClickListener {
            onStartButtonClicked()
        }

        binding.stopButton.setOnClickListener {
            onStopButtonClicked()
        }

        binding.resetButton.setOnClickListener {

        }

        return binding.root
    }

    private fun onStartButtonClicked() {
        if (hasBackgroundLocationPermission(requireContext())) {

            startCountDown()
            binding.startButton.disable()
            binding.startButton.hide()
            binding.stopButton.show()

        } else {
            requestBackgroundLocationPermission(this)
        }
    }

    private fun onStopButtonClicked() {
        stopForegroundService()
        binding.stopButton.hide()
        binding.startButton.show()
    }

    private fun stopForegroundService() {
        binding.startButton.disable()
        sendActionCommandToService(ACTION_SERVICE_STOP)
    }

    private fun startCountDown() {
        binding.timerTextView.show()
        binding.stopButton.disable()

        val timer: CountDownTimer = object : CountDownTimer(4000, 1000) {
            override fun onFinish() {
                sendActionCommandToService(ACTION_SERVICE_START)
                binding.timerTextView.hide()
            }

            override fun onTick(millisUntilFinished: Long) {
                val currentSecond = millisUntilFinished / 1000

                if (currentSecond.toString() == "0") {
                    binding.timerTextView.text = "GO"
                    binding.timerTextView.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.black
                        )
                    )
                } else {
                    binding.timerTextView.text = currentSecond.toString()
                    binding.timerTextView.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.red
                        )
                    )
                }


            }

        }

        timer.start()

    }

    private fun sendActionCommandToService(action: String) {
        Intent(
            requireContext(),
            TrackerService::class.java
        ).apply {
            this.action = action
            requireActivity().startService(this)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms[0])) {
            SettingsDialog.Builder(requireActivity()).build().show()
        } else {
            requestBackgroundLocationPermission(this)
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        onStartButtonClicked()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap?) {
        map = googleMap!!

        map.isMyLocationEnabled = true
        map.setOnMyLocationButtonClickListener(this)

        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isZoomGesturesEnabled = false
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            isCompassEnabled = false
            isScrollGesturesEnabled = false
        }

        observeTrackerService()
    }

    private fun followPolyLine() {
        if (locationList.isNotEmpty()) {
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(setCameraPosition(locationList.last())),
                100,
                null
            )
        }
    }

    private fun observeTrackerService() {
        TrackerService.locationList.observe(viewLifecycleOwner, {
            if (it != null) {
                locationList = it
                if (locationList.size > 1) {
                    binding.stopButton.enable()
                }
                drawPolyline()
                followPolyLine()
            }
        })
    }

    private fun drawPolyline() {
        var polyLine = map.addPolyline(
            PolylineOptions().apply {
                width(10f)
                color(Color.BLUE)
                jointType(JointType.ROUND)
                startCap(ButtCap())
                endCap(ButtCap())
                addAll(locationList)
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onMyLocationButtonClick(): Boolean {

        binding.hintTextView.animate().alpha(0f).duration = 1500

        lifecycleScope.launch {
            delay(2500)
            binding.hintTextView.hide()
            binding.hintTextView.show()
        }
        return false
    }

}