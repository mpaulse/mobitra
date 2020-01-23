Development Notes
===

## TODO:

- Integrate UI with networking back-end, with a timer to periodically collect and reconcile data from Huawei and Telkom.
- Historic data removal after retention period.
- Save unrecorded data usage on exit.
- Realtime traffic updates for chart popups.

## Ideas:

- Create a separate Raspberry Pi app to do continuous monitoring, even while PC is shutdown.
- Create a separate Windows service to do monitoring, so that no Windows login is required
  (e.g. using [Procrun](http://commons.apache.org/proper/commons-daemon/procrun.html)).

## Bugs:

- On first load, no active product not shown. Active Products screen says "No data available".
  Status bar text says: "SIM: Unknown     Current Product: Unknown". Show monitoring error on status bar.
2020-01-23 22:14:27,052 ERROR [DefaultDispatcher-worker-3] com.mpaulse.mobitra.DataUsageMonitor - Data usage monitoring error
com.mpaulse.mobitra.net.MonitoringAPIException: POST https://onnetsecure.telkom.co.za:443/onnet/public/api/getFreeResources failed: response status 302
java.net.http.HttpHeaders@75d7eaf2 { {content-language=[en], content-length=[331], content-type=[text/plain], date=[Thu, 23 Jan 2020 20:14:27 GMT], location=[http://onnetsecure.telkom.co.za/onnet/public/api/loggedOut?version=1], server=[Oracle-HTTP-Server-11g Oracle-Web-Cache-11g/11.1.1.6.0 (N;ecid=84720070977817868,0:1:1)], set-cookie=[JSESSIONID=56HDpp2DynHyQdfhLG2sQnSv1dZ4qCM82QzwvXpqPhtfghGD7dzC!-372627579; path=/; HttpOnly], x-powered-by=[Servlet/2.5 JSP/2.1]} }
<html><head><title>302 Moved Temporarily</title></head>
<body bgcolor="#FFFFFF">
<p>This document you requested has moved temporarily.</p>
<p>It's now at <a href="http://onnetsecure.telkom.co.za/onnet/public/api/loggedOut?version=1">http://onnetsecure.telkom.co.za/onnet/public/api/loggedOut?version=1</a>.</p>
</body></html>

	at com.mpaulse.mobitra.net.MonitoringAPIClient.doHttpRequest(MonitoringAPIClient.kt:202)
	at com.mpaulse.mobitra.net.MonitoringAPIClient.doUrlEncodedHttpPost(MonitoringAPIClient.kt:189)
	at com.mpaulse.mobitra.net.MonitoringAPIClient.access$doUrlEncodedHttpPost(MonitoringAPIClient.kt:59)
	at com.mpaulse.mobitra.net.MonitoringAPIClient$getTelkomFreeResources$3.invokeSuspend(MonitoringAPIClient.kt:164)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:56)
	at kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:561)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.executeTask(CoroutineScheduler.kt:727)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker(CoroutineScheduler.kt:667)
	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:655)


## Huawei LTE Router API:

GET http://192.168.1.254/api/monitoring/month_statistics

```
<?xml version="1.0" encoding="UTF-8"?>
<response>
<CurrentMonthDownload>10179248566</CurrentMonthDownload>
<CurrentMonthUpload>350146232</CurrentMonthUpload>
<MonthDuration>175201</MonthDuration>
<MonthLastClearTime>2019-10-29</MonthLastClearTime>
</response>
```
---
GET http://192.168.1.254/api/monitoring/traffic-statistics

```
<?xml version="1.0" encoding="UTF-8"?>
<response>

current mobile network connection session
<CurrentConnectTime>170010</CurrentConnectTime> <- sec
<CurrentUpload>318170852</CurrentUpload>  <- bytes
<CurrentDownload>9845754134</CurrentDownload> <- bytes
<CurrentDownloadRate>996</CurrentDownloadRate> <- Bps?
<CurrentUploadRate>160</CurrentUploadRate>

month stats
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

POST https://onnetsecure.telkom.co.za/onnet/public/api/getFreeResources

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
