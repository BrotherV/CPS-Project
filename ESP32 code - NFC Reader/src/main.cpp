#include <Arduino.h>
#include <painlessMesh.h>

/////////////////////////////////////////////////////////////////////////////////////////////////////
#define Log_M(s) Serial.print(F(s))
#define Log_D(s) Serial.println(s)

#define MESH_PREFIX "Network_Al001"
#define MESH_PASSWORD "@Cps#2022$"
#define MESH_PORT 8080

#define RD_CTRL   19
#define WR_CTRL   18
#define RD_STATUS 26
#define BY_STATUS 23

#define SERIAL_BUFFER_SIZE_RX 256 // make it big enough to hold your longest command
#define LF '\n'
#define CR '\r'

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// QueueHandle_t queue_1;
painlessMesh mesh;
Scheduler ts;
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
void sendMeshData(String key, bool val);

void readSerial();
Task tSerial(TASK_MILLISECOND * 10, TASK_FOREVER, &readSerial, &ts, true);

void meshUpdate();
Task tMesh(TASK_IMMEDIATE, TASK_FOREVER, &meshUpdate, &ts, true);

void lowReadPin();
void upReadPin();
Task tRead(TASK_IMMEDIATE, TASK_ONCE, &lowReadPin, &ts);

void lowWritePin();
void upWritePin();
Task tWrite(TASK_IMMEDIATE, TASK_ONCE, &lowWritePin, &ts);

void readInputs();
Task tReadInputs(TASK_MILLISECOND * 250, TASK_FOREVER, &readInputs, &ts);

uint32_t devId;
char serialBuffer[SERIAL_BUFFER_SIZE_RX + 1]; // +1 allows space for the null terminator
int serialBufferOffset = 0;                   // number of characters currently in the buffer

bool hasLF = false;
bool isBusy, isDataRead;
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
void lowReadPin()
{
        tRead.setCallback(&upReadPin);
        digitalWrite(RD_CTRL, LOW);
        tRead.restartDelayed(100);
}

void upReadPin()
{
        tRead.setCallback(&lowReadPin);
        digitalWrite(RD_CTRL, HIGH);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
void lowWritePin()
{
        tWrite.setCallback(&upWritePin);
        digitalWrite(WR_CTRL, LOW);
        tWrite.restartDelayed(100);
}

void upWritePin()
{
        tWrite.setCallback(&lowWritePin);
        digitalWrite(WR_CTRL, HIGH);
}
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
void readInputs()
{
  if(!digitalRead(BY_STATUS) && digitalRead(RD_STATUS) && !isBusy){
    isBusy = true;
    Serial.println("Line is busy.");
  }else if(digitalRead(BY_STATUS) && !digitalRead(RD_STATUS) && !isDataRead){
    isBusy = false;
    isDataRead = true;
    tReadInputs.disable();
    sendMeshData("card_read", true);
    Serial.println("Data read successfuly");
  }else if(digitalRead(BY_STATUS) && !digitalRead(RD_STATUS) && isDataRead){
    isBusy = false;
    isDataRead = true;
    sendMeshData("card_copied", true);
    tReadInputs.disable();
    Serial.println("Data copied successfuly");
  }else if(digitalRead(BY_STATUS) && digitalRead(RD_STATUS) && isDataRead){
    isBusy = false;
    isDataRead = false;
    sendMeshData("card_copied", false);
    tReadInputs.disable();
    Serial.println("Data copied failed");
  }else if(digitalRead(RD_STATUS) && digitalRead(BY_STATUS) && !isDataRead){
    isBusy = false;
    Serial.println("No card inserted");
    if(!tRead.isEnabled() && tReadInputs.enable()){
      tRead.restartDelayed(350);
    }
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

void readSerial()
{
  if (Serial.available() > 0)
  {
    while (Serial.available())
    {
      char c = Serial.read();
      Serial.print(c);
      switch (c)
      {
      case LF:
        hasLF = true;
        break;
      default:
        if (serialBufferOffset < SERIAL_BUFFER_SIZE_RX)
          serialBuffer[serialBufferOffset++] = c;
        break;
      }
      // if (serialBufferOffset < SERIAL_BUFFER_SIZE_RX)
      //   serialBuffer[serialBufferOffset++] = c;
    }

    if(hasLF){
      if(strcmp(serialBuffer, "1") == 0){
        Serial.println("\tRead Card");
        tRead.restart();
      }else if(strcmp(serialBuffer, "2") == 0){
        Serial.println("\tWrite Card");
        tWrite.restart();
      }

      for (int i = 0; i <= SERIAL_BUFFER_SIZE_RX; i++)
          serialBuffer[i] = 0;
      serialBufferOffset = 0;
    }
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
void meshUpdate()
{
  mesh.update();
}

//////////////////////////////////////////////Mesh Callback Methods///////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Needed for painless library
void sendMeshData(String key, bool val)
{
  if(devId != 0){
    StaticJsonDocument<128> replyDoc;
    replyDoc["nodeId"] = mesh.getNodeId();
    replyDoc[key] = val;
    String reply;
    // Generate the minified JSON and send it to the Serial port.
    //
    serializeJson(replyDoc, reply);
    mesh.sendSingle(devId, reply);
  }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
void receivedCallback(uint32_t from, String &msg)
{
  Serial.printf("\n\tstartHere: Received from %u msg=%s\n", from, msg.c_str());
  devId = from;
  // StaticJsonDocument<384> doc;
  DynamicJsonDocument doc(384);
  DeserializationError error = deserializeJson(doc, msg);

  if (error)
  {
    Log_M("\n\tdeserializeJson() failed: ");
    Log_D(error.f_str());
  }
  else
  {
    if (doc["status"] != nullptr && doc["status"].as<bool>())
    {
      //readInputs();
      if(tReadInputs.isEnabled() && !isDataRead){
        sendMeshData("started", true);
      }else if(isDataRead){
        sendMeshData("card_read", true);
      }
    }
    else if (doc["start"] != nullptr && doc["start"].as<bool>())
    {
      if(!tReadInputs.isEnabled())
      {
        isDataRead = false;
        tRead.restart();
        tReadInputs.restartDelayed(150);
        sendMeshData("started", true);
      }
    }
    else if (doc["stop"] != nullptr && doc["stop"].as<bool>())
    {
      tReadInputs.disable();
      sendMeshData("started", false);
    }
    else if (doc["copy"] != nullptr && doc["copy"].as<bool>())
    {
      tWrite.restart();
      tReadInputs.restartDelayed(150);
    }
  }
}

void newConnectionCallback(uint32_t nodeId) {
    Serial.printf("--> startHere: New Connection, nodeId = %u\n", nodeId);
}

void changedConnectionCallback() {
  Serial.printf("Changed connections\n");
}

void nodeTimeAdjustedCallback(int32_t offset) {
    Serial.printf("Adjusted time %u. Offset = %d\n", mesh.getNodeTime(),offset);
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
void setup() {
  Serial.begin(115200); 
  Serial.println("Initializing...");

  pinMode(RD_CTRL, OUTPUT);
  pinMode(WR_CTRL, OUTPUT);
  pinMode(RD_STATUS, INPUT);
  pinMode(BY_STATUS, INPUT);

  digitalWrite(RD_CTRL, HIGH);
  digitalWrite(WR_CTRL, HIGH);

  mesh.setDebugMsgTypes(ERROR | STARTUP); // set before init() so that you can see startup messages
  //mesh.setDebugMsgTypes(ERROR | STARTUP | CONNECTION);
  mesh.init(MESH_PREFIX, MESH_PASSWORD, MESH_PORT, WIFI_AP_STA, 6);
  mesh.onReceive(&receivedCallback);
  // mesh.onNewConnection(&newConnectionCallback);
  // mesh.onChangedConnections(&changedConnectionCallback);
  // mesh.onNodeTimeAdjusted(&nodeTimeAdjustedCallback);
  tWrite.restart();
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
void loop() {
  ts.execute();
  //mesh.update();
}
