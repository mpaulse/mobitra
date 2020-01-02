Development Notes
===

## TODO:

- Settings screen:
  - Huawei IP address.
  - Launch at Windows start-up option.
  - Test Connection button.
- About screen.
- Launch at Windows start-up, if configured.
- Integrate UI with networking back-end, with a timer to periodically collect and reconcile data from Huawei and Telkom.
- Prevent multiple instances of application.

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
