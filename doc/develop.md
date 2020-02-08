Development Notes
===

## TODO:

- Switch to official jpackage tool in JDK 14.
- Check for multiple application instances after switch to JDK14's jpackage tool.
- Disable debug logging.
- Historic data removal after retention period.

## Bugs:
- Available amount in status bar, active products menu and cummulative chart label not
  updating.
- Overlapping x-axis tick labels on the cumulative chart, e.g. the Today date overlaps
  Activation or Expiry.
- The "used" label overlaps the "total" label on the cumulative chart when the data is
  exhausted and the current date is near or equal to the expiry date. 

## Ideas:
- Create a separate Raspberry Pi app to do continuous monitoring, even while PC is shutdown.
- Create a separate Windows service to do monitoring, so that no Windows login is required
  (e.g. using [Procrun](http://commons.apache.org/proper/commons-daemon/procrun.html)).

## Huawei LTE Router API:

**NOTE:** Some devices (e.g. E5573) needs the SessionID cookie returned by ```GET http://ROUTER-IP-ADDRESS```.

---

GET http://ROUTER-IP-ADDRESS/api/wlan/basic-settings

```
<?xml version="1.0" encoding="UTF-8"?>
<response>
<WifiSsid>MY-MOBILE</WifiSsid>
<WifiChannel>0</WifiChannel>
<WifiHide>0</WifiHide>
<WifiCountry>ZA</WifiCountry>
<WifiMode>b&#x2F;g&#x2F;n</WifiMode>
<WifiRate>0</WifiRate>
<WifiTxPwrPcnt>100</WifiTxPwrPcnt>
<WifiMaxAssoc>16</WifiMaxAssoc>
<WifiEnable>1</WifiEnable>
<WifiFrgThrshld>2346</WifiFrgThrshld>
<WifiRtsThrshld>2347</WifiRtsThrshld>
<WifiDtmIntvl>1</WifiDtmIntvl>
<WifiBcnIntvl>100</WifiBcnIntvl>
<WifiWme>1</WifiWme>
<WifiPamode>0</WifiPamode>
<WifiIsolate>0</WifiIsolate>
<WifiProtectionmode>1</WifiProtectionmode>
<Wifioffenable>1</Wifioffenable>
<Wifiofftime>600</Wifiofftime>
<wifibandwidth>20</wifibandwidth>
<wifiautocountryswitch>1</wifiautocountryswitch>
<wifiantennanum>2</wifiantennanum>
<wifiguestofftime>0</wifiguestofftime><WifiRestart>0</WifiRestart>
</response>
```

---

GET http://ROUTER-IP-ADDRESS/api/device/basic_information

```
<?xml version="1.0" encoding="UTF-8"?>
<response>
<productfamily>LTE</productfamily>
<classify>mobile-wifi</classify>
<multimode>0</multimode>
<restore_default_status>0</restore_default_status>
<sim_save_pin_enable>0</sim_save_pin_enable>
<devicename>E5573Cs-322</devicename>
</response>
```

---

GET http://ROUTER-IP-ADDRESS/api/device/information

**NOTE**: Needs admin login SessionID cookie.

```
<?xml version="1.0" encoding="UTF-8"?>
<response>
<DeviceName>E5573Cs-322</DeviceName>
<SerialNumber>5LL7S181300123456</SerialNumber>
<Imei>12345</Imei>
<Imsi>6789</Imsi>
<Iccid>012345</Iccid>
<Msisdn></Msisdn>
<HardwareVersion>CL1E5573CSM11 Ver.B</HardwareVersion>
<SoftwareVersion>21.318.03.00.778</SoftwareVersion>
<WebUIVersion>17.100.08.01.983</WebUIVersion>
<MacAddress1>12:34:56:78:18:5A</MacAddress1>
<MacAddress2></MacAddress2>
<ProductFamily>LTE</ProductFamily>
<Classify>mobile-wifi</Classify>
<supportmode>LTE|WCDMA|GSM</supportmode>
<workmode>LTE</workmode>
</response>
```

---

GET http://ROUTER-IP-ADDRESS/api/monitoring/status
```
<?xml version="1.0" encoding="UTF-8"?>
<response>
<ConnectionStatus>901</ConnectionStatus>
<WifiConnectionStatus>902</WifiConnectionStatus>
<SignalStrength></SignalStrength>
<SignalIcon>4</SignalIcon>
<CurrentNetworkType>19</CurrentNetworkType>
<CurrentServiceDomain>3</CurrentServiceDomain>
<RoamingStatus>0</RoamingStatus>
<BatteryStatus>1</BatteryStatus>
<BatteryLevel>4</BatteryLevel>
<BatteryPercent>100</BatteryPercent>
<simlockStatus>0</simlockStatus>
<WanIPAddress>100.1.2.345</WanIPAddress>
<WanIPv6Address></WanIPv6Address>
<PrimaryDns>196.1.2.345</PrimaryDns>
<SecondaryDns>105.1.2.345</SecondaryDns>
<PrimaryIPv6Dns></PrimaryIPv6Dns>
<SecondaryIPv6Dns></SecondaryIPv6Dns>
<CurrentWifiUser>1</CurrentWifiUser>
<TotalWifiUser>16</TotalWifiUser>
<currenttotalwifiuser>16</currenttotalwifiuser>
<ServiceStatus>2</ServiceStatus>
<SimStatus>1</SimStatus>
<WifiStatus>1</WifiStatus>
<CurrentNetworkTypeEx>101</CurrentNetworkTypeEx>
<WanPolicy>0</WanPolicy>
<maxsignal>5</maxsignal>
<wifiindooronly>0</wifiindooronly>
<wififrequence>0</wififrequence>
<classify>mobile-wifi</classify>
<flymode>0</flymode>
<cellroam>1</cellroam>
<ltecastatus>0</ltecastatus>
</response>
```
---

GET http://ROUTER-IP-ADDRESS/api/monitoring/traffic-statistics

```
<?xml version="1.0" encoding="UTF-8"?>
<response>
<CurrentConnectTime>170010</CurrentConnectTime>
<CurrentUpload>318170852</CurrentUpload>
<CurrentDownload>9845754134</CurrentDownload>
<CurrentDownloadRate>996</CurrentDownloadRate>
<CurrentUploadRate>160</CurrentUploadRate>
<TotalUpload>350674492</TotalUpload> 
<TotalDownload>10211903746</TotalDownload>
<TotalConnectTime>175243</TotalConnectTime>
<showtraffic>1</showtraffic>
</response>
```


## Telkom API:

POST http://onnet.telkom.co.za/onnet/public/api/checkOnnet

Body Parameters:
- None needed

Response
```
{
    "resultCode": 0,
    "resultMessageCode": "api-co-002",
    "resultMessage": "Onnet session successfully established.",
    "friendlyCustomerMessage": "",
    "payload": {
        "sessionToken": "8474625425622783908",
        "friendlySecurityLevel": "Unprotected",
        "securityLevel": 0,
        "response": null,
        "secureHost": "onnetsecure.telkom.co.za"
    }
}
```
---

POST https://onnetsecure.telkom.co.za/onnet/public/api/createOnnetSession

Body Parameters (x-www-form-urlencoded):
```
sid = sessionToken from checkOnnet response
```

Response
```
{
    "resultCode": 0,
    "resultMessageCode": "api-cos-005",
    "resultMessage": "Onnet session created",
    "friendlyCustomerMessage": "",
    "payload": {
        "msisdn": "0123456789"
    }
}
```

---

POST https://onnetsecure.telkom.co.za/onnet/public/api/getFreeResources

Headers:
```
Cookie: JSESSIONID from createOnnetSession
```
Body Parameters (x-www-form-urlencoded):
```
msisdn = msisdn from createOnnetSession
```

Response
- Look for service = GRPS
- endBillCycle = expiry date (exlusive - previous day last usable day)
- Type codes to exclude (apparently):
          "5124",
          "5749",
          "5135",
          "5136",
          "5149",
          "5177"

```

{
    "resultCode": 0,
    "resultMessageCode": "api-gfr-009",
    "resultMessage": "Free resources successfully retrieved",
    "friendlyCustomerMessage": "",
    "payload": [
        {
            "subscriberFreeResource": {
                "type": "5036",
                "typeName": "Campaign Welcome Bonus Messaging",
                "service": "SMS/MMS",
                "totalAmount": "5",
                "totalAmountAndMeasure": "5 Items",
                "usedAmount": "0",
                "usedAmountAndMeasure": "0 Items",
                "measure": "Items",
                "startBillCycle": "Tue Nov 05 2019",
                "endBillCycle": "00:00:00 Tue Nov 05 2019",
                "isTimeBased": false
            },
            "info": "SMS/MMS: 5 Items remaining 0 Items used  Expires on Tue Nov 05 2019",
            "service": "SMS/MMS"
        },
        {
            "subscriberFreeResource": {
                "type": "5125",
                "typeName": "Once-off LTE/LTE-A Night Surfer Data",
                "service": "GPRS",
                "totalAmount": "64183731327",
                "totalAmountAndMeasure": "61210 MB",
                "usedAmount": "240778113",
                "usedAmountAndMeasure": "230 MB",
                "measure": "Bytes",
                "startBillCycle": "Fri Nov 29 2019",
                "endBillCycle": "00:00:00 Fri Nov 29 2019",
                "isTimeBased": false
            },
            "info": "GPRS: 64183731327 Bytes remaining 240778113 Bytes used  Expires on Fri Nov 29 2019",
            "service": "GPRS"
        },
        {
            "subscriberFreeResource": {
                "type": "5127",
                "typeName": "Once-off LTE/LTE-A Anytime Data",
                "service": "GPRS",
                "totalAmount": "53002844210",
                "totalAmountAndMeasure": "50547 MB",
                "usedAmount": "11421665230",
                "usedAmountAndMeasure": "10893 MB",
                "measure": "Bytes",
                "startBillCycle": "Sun Dec 29 2019",
                "endBillCycle": "00:00:00 Sun Dec 29 2019",
                "isTimeBased": false
            },
            "info": "GPRS: 53002844210 Bytes remaining 11421665230 Bytes used  Expires on Sun Dec 29 2019",
            "service": "GPRS"
        }
    ]
}
```

Response if Onnet session not active or expired
```
{
    "resultCode": 1,
    "resultMessageCode": "api-lo-001",
    "resultMessage": "User has lost onnet session",
    "friendlyCustomerMessage": "",
    "payload": {}
}
```
