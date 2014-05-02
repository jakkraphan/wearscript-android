package com.dappervision.wearscript.managers;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.RemoteException;

import com.dappervision.wearscript.BackgroundService;
import com.dappervision.wearscript.Log;
import com.dappervision.wearscript.Utils;
import com.dappervision.wearscript.WearScript;
import com.dappervision.wearscript.events.SendEvent;
import com.dappervision.wearscript.events.SensorJSEvent;
import com.radiusnetworks.ibeacon.IBeacon;
import com.radiusnetworks.ibeacon.IBeaconConsumer;
import com.radiusnetworks.ibeacon.RangeNotifier;
import com.radiusnetworks.ibeacon.Region;

import java.util.Collection;

import com.radiusnetworks.ibeacon.IBeaconConsumer;
import com.radiusnetworks.ibeacon.MonitorNotifier;
import com.radiusnetworks.ibeacon.Region;

public class IBeaconManager extends Manager implements IBeaconConsumer{

    com.radiusnetworks.ibeacon.IBeaconManager iBeaconManager;

    Region MONITOR_REGION = new Region("myMonitoringUniqueId", null, null, null);
    Region RANGING_REGION = new Region("myRangingUniqueId", null, null, null);

    public IBeaconManager(BackgroundService bs) {
        super(bs);
    }

    @Override
    public void reset() {
        super.reset();
        ibeaconSensorOff();
        ibeaconSensorOn();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        ibeaconSensorOff();
    }

    public void ibeaconSensorOn() {
        iBeaconManager = com.radiusnetworks.ibeacon.IBeaconManager.getInstanceForApplication(service);
        iBeaconManager.bind(this);
    }

    public void ibeaconSensorOff() {
        if(iBeaconManager == null)
            return;
        try {
            iBeaconManager.stopMonitoringBeaconsInRegion(MONITOR_REGION);
            iBeaconManager.stopRangingBeaconsInRegion(RANGING_REGION);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        iBeaconManager.unBind(this);
        iBeaconManager = null;
    }

    public void onEvent(SensorJSEvent event) {
        int type = event.getType();
        if(event.getStatus()) {
            if (type == WearScript.SENSOR.IBEACON.id())
                ibeaconSensorOn();
        }
        else {
            ibeaconSensorOff();
        }
    }

    @Override
    public void onIBeaconServiceConnect() {
        iBeaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<IBeacon> iBeacons, Region region) {
                for(IBeacon myBeacon : iBeacons){
                    if(myBeacon.getAccuracy() >= 0)
                        Utils.eventBusPost(new SendEvent("ibeacon:"+myBeacon.getProximityUuid(), myBeacon.getProximityUuid(), myBeacon.getRssi(), myBeacon.getMajor(), myBeacon.getMinor()));
                }
            }
        });

        try {
            iBeaconManager.startRangingBeaconsInRegion(RANGING_REGION);
        } catch (RemoteException e) {   }

        iBeaconManager.setMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                Log.i(TAG, "I just saw an iBeacon for the firt time!");
                Utils.eventBusPost(new SendEvent("ibeacon:enter", region.getProximityUuid(), region.getMajor(), region.getMinor()));
            }

            @Override
            public void didExitRegion(Region region) {
                Log.i(TAG, "I no longer see an iBeacon");
                Utils.eventBusPost(new SendEvent("ibeacon:exit", region.getProximityUuid(), region.getMajor(), region.getMinor()));
            }

            @Override
            public void didDetermineStateForRegion(int state, Region region) {
                Log.i(TAG, "I have just switched from seeing/not seeing iBeacons: "+state);
            }
        });

        try {
            iBeaconManager.startMonitoringBeaconsInRegion(MONITOR_REGION);
        } catch (RemoteException e) {   }
    }

    // Methods usually implemented by the activity
    @Override
    public boolean bindService(Intent intent, ServiceConnection connection, int mode) {
        return service.bindService(intent, connection, mode);
    }

    @Override
    public void unbindService(ServiceConnection connection) {
        service.unbindService(connection);
    }

    @Override
    public Context getApplicationContext() {
        return service;
    }
}