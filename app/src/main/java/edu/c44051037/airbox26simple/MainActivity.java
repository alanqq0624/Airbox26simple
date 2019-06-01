package edu.c44051037.airbox26simple;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener{

    private Button button_paired, button_disconnect, button_change;
    private TextView text_status, text_data, text_air;
    private ListView list_view;
    private ImageView image;

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothSocket mBluetoothSocket = null;
    private ArrayAdapter<String> deviceName;
    private ArrayAdapter<String> deviceID;
    private BluetoothDevice chosenDevice = null;
    private Set<BluetoothDevice> pairedDevice;
    private String choseID = null;
    private static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private InputStream inputStream = null;
    private OutputStream outputStream = null;

    Thread receiveThread;
    volatile boolean stopThread = true;
    private int readBufferPosition;
    private byte[] readBuffer;
    private String UID;
    private int counter = 0;
    private LocationManager locMgr;
    private String bestProv;
    private boolean CONNECTED = false;
    private float current_PM = 75;

    private Calendar calendar;
    private SimpleDateFormat dateform = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setToolbar(toolbar);//do this before do serSupportActionbar()
        setSupportActionBar(toolbar);

        deviceName = new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1);
        deviceID = new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1);
        setView();
        setListener();
        text_status.setText("Hello, Please paired the Airbox first,\nand remember the bluetooth name of the airbox before using this program\n");
    }

    private void setView(){
        button_disconnect = (Button) findViewById(R.id.disconnect);
        button_paired = (Button) findViewById(R.id.paired);
        button_change = (Button) findViewById(R.id.change);
        text_status = (TextView) findViewById(R.id.status);
        text_status.setMovementMethod(new ScrollingMovementMethod());
        text_data = (TextView) findViewById(R.id.PMData);
        text_air = (TextView) findViewById(R.id.air);
        list_view = (ListView) findViewById(R.id.list);
        image = (ImageView) findViewById(R.id.Image);
    }

    private void setListener(){
        button_paired.setOnClickListener(this);
        button_disconnect.setOnClickListener(this);
        list_view.setOnItemClickListener(this);
        button_change.setOnClickListener(this);
    }

    private void setToolbar(Toolbar toolbar){
        toolbar.setTitle("Airbox Data Receiver");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_about) {
            Toast.makeText(MainActivity.this, "阿被你發現功能未完成拉w\n不過製作人員名單在Log區喔", Toast.LENGTH_LONG).show();
            statusAppend("Project: Airbox26\n  Ardunio: 陳志謙\n  3D printing: 林啟允\n  APP making: 謝達永\n  特別感謝: Google大神");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        if (v == findViewById(R.id.paired)){
            image.setVisibility(View.INVISIBLE);
            text_data.setVisibility(View.INVISIBLE);
            text_air.setVisibility(View.INVISIBLE);
            button_change.setText("View");
            findBlue();
        }
        if (v == findViewById(R.id.disconnect)) {
            try {
                closeBlue();
            } catch (IOException e) {
                statusAppend("Close Bluetooth Failed");
                e.printStackTrace();
            }
        }
        if (v == findViewById(R.id.change)){
            if (CONNECTED == true) {
                if (image.getVisibility() == View.VISIBLE) {
                    image.setVisibility(View.INVISIBLE);
                    text_data.setVisibility(View.INVISIBLE);
                    text_air.setVisibility(View.INVISIBLE);
                    button_change.setText("View");
                    statusAppend("Had switch to Log Page");
                } else {
                    image.setVisibility(View.VISIBLE);
                    text_data.setVisibility(View.VISIBLE);
                    text_air.setVisibility(View.VISIBLE);
                    button_change.setText("Log");
                    statusAppend("Had switch to View Page");
                }
            }
            else {
                statusAppend("Can't open View Page with no connect");
            }
        }
    }

    private void findBlue(){
        deviceName.clear();
        deviceID.clear();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null){
            //no bluetooth device available
            //no device had paired with this deviced before
        }
        if (!mBluetoothAdapter.isEnabled()){
            Intent enableBlue = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBlue, 1);
        }
        pairedDevice = mBluetoothAdapter.getBondedDevices();
        if (pairedDevice.size() > 0){
            for (BluetoothDevice device : pairedDevice){
                UID = device.getAddress();

                deviceName.add(device.getName()+"\n"+UID);
                deviceID.add(UID);
            }
            list_view.setAdapter(deviceName);
            statusAppend("已配對裝置已找到"+pairedDevice.size()+"個");
        }
    }

    private void connectBlue() throws IOException{
        if (chosenDevice != null){
            statusAppend("Had selected Device: "+chosenDevice.getName());
            statusAppend("Loading...");
            mBluetoothSocket = chosenDevice.createRfcommSocketToServiceRecord(myUUID);
            try {
                mBluetoothSocket.connect();
                CONNECTED = true;
            }catch (IOException conIOE){
                try{
                    statusAppend("Boothtooth connect failed");
                    conIOE.printStackTrace();
                    mBluetoothSocket.close();
                }catch (IOException clIOE){
                    statusAppend("socket close failed");
                    clIOE.printStackTrace();
                }
                return;
            }
            inputStream = mBluetoothSocket.getInputStream();
            outputStream = mBluetoothSocket.getOutputStream();

            GetData();

            statusAppend("Bluetooth "+chosenDevice.getName()+" "+chosenDevice.getAddress()+" connect");

            image.setVisibility(View.VISIBLE);
            text_data.setVisibility(View.VISIBLE);
            text_air.setVisibility(View.VISIBLE);
            button_change.setText("Log");
        }
    }

    private void closeBlue() throws IOException {
        if (CONNECTED == true) {
            statusAppend("Bluetooth Disconnect start.");
            try {
                stopThread = true;
                inputStream.close();
                outputStream.close();
                mBluetoothSocket.close();
                deviceName.clear();
                deviceID.clear();
                chosenDevice = null;
                choseID = null;
            } catch (Exception ee) {
                statusAppend("Bluetooth Disconnect face some trouble");
                ee.printStackTrace();
            }
            image.setVisibility(View.INVISIBLE);
            text_data.setVisibility(View.INVISIBLE);
            text_air.setVisibility(View.INVISIBLE);
            button_change.setText("View");
            CONNECTED = false;
            statusAppend("Bluetooth Disconnect success.");
        }
        else{
            statusAppend("No Device have connect now.");
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        choseID = deviceID.getItem(position);
        for (BluetoothDevice device : pairedDevice){
            if (device.getAddress() == choseID){
                chosenDevice = device;
                break;
            }
        }
        statusAppend("Device select: " + choseID);
        try {
            connectBlue();
        }catch (IOException e){
            statusAppend("Device connect Failed");
            e.printStackTrace();
        }

        deviceName.clear();
        deviceID.clear();
        //list_view.setVisibility(view.INVISIBLE);
    }

    private void GetData(){
        final Handler handler = new Handler();
        final byte delimeter = 10;

        stopThread = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        //receiveThread = new Thread(new Runnable() {})
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopThread){
                    try{
                        int byteAvailable = inputStream.available();
                        if (byteAvailable > 0){
                            byte[] readerBao = new byte[byteAvailable];
                            inputStream.read(readerBao);
                            for (int i = 0; i < byteAvailable; i++){
                                byte in = readerBao[i];
                                if(in == delimeter){
                                    byte[] encodeByte = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodeByte, 0, encodeByte.length);
                                    final String data = new String(encodeByte, "US-ASCII");
                                    readBufferPosition = 0;
                                    counter++;
                                    handler.post(new Runnable() {
                                                     @Override
                                                     public void run() {
                                                         //String preStr = text_status.getText().toString();
                                                         //String showText = String.format("%s\nCF=PM2.5=%sug/m3,receive data label = %s", preStr, data, counter);
                                                         statusAppend("PM2.5 = "+data+"ug/m^3");
                                                         text_data.setText(data);
                                                         StoF(data);
                                                         change_image();
                                                     }
                                                 }
                                    );
                                }
                                else {
                                    readBuffer[readBufferPosition++] = in;
                                }
                            }
                        }
                    }catch (IOException ioe){
                        stopThread = true;
                    }
                }
            }
        }).start();
    }

    private void StoF(String in) {
        try {
            current_PM = Float.parseFloat(in);
        }catch (NumberFormatException NFE){
            statusAppend("NumberFormatException");
            NFE.printStackTrace();
        }
    }

    private void statusAppend(String in){
        text_status.append("\n"+getTime()+"\n  "+in);

        int offset = text_status.getLineCount()*text_status.getLineHeight();
        if (offset > text_status.getHeight()){
            text_status.scrollTo(0, offset-text_status.getHeight());
        }
    }

    private String getTime(){
        calendar = Calendar.getInstance();
        return "["+dateform.format(calendar.getTime())+"]";
    }

    private void change_image(){
        if (CONNECTED == false){
            return;
        }
        if(current_PM >= 0 && current_PM < 8){
            image.setImageDrawable(getResources().getDrawable(R.drawable.a01));
            text_air.setText("是不是剛下過雨啊?");
        }
        if(current_PM >= 8 && current_PM <16){
            image.setImageDrawable(getResources().getDrawable(R.drawable.a02));
            text_air.setText("你家是住森林嗎");
        }
        if(current_PM >= 16 && current_PM < 24){
            image.setImageDrawable(getResources().getDrawable(R.drawable.a03));
            text_air.setText("你家空氣超好");
        }
        if(current_PM >= 24 && current_PM < 32){
            image.setImageDrawable(getResources().getDrawable(R.drawable.a04));
            text_air.setText("聽說只要1500萬人\n同時搧風\n就可以趕霧霾");
        }
        if(current_PM >= 32 && current_PM < 40){
            image.setImageDrawable(getResources().getDrawable(R.drawable.a05));
            text_air.setText("好像空氣有點糟");
        }
        if(current_PM >= 40 && current_PM < 48){
            image.setImageDrawable(getResources().getDrawable(R.drawable.a06));
            text_air.setText("這空氣少出門吧\nPS:聽說PM2.5戴口罩沒用");
        }
        if(current_PM >= 48 && current_PM < 56){
            image.setImageDrawable(getResources().getDrawable(R.drawable.a07));
            text_air.setText("宅在家裡的好理由 :\n外面空氣太糟");
        }
        if(current_PM >= 56 && current_PM < 64){
            image.setImageDrawable(getResources().getDrawable(R.drawable.a08));
            text_air.setText("JIZZ般的空氣品質\nJIZZ般的PM2.5");
        }
        if(current_PM >= 64 && current_PM < 71){
            image.setImageDrawable(getResources().getDrawable(R.drawable.a09));
            text_air.setText("你家是住\n火力發電廠旁嗎?\n空氣很糟阿");
        }
        if(current_PM >= 71 && current_PM < 85){
            image.setImageDrawable(getResources().getDrawable(R.drawable.a10));
            text_air.setText("紫爆RRRRR\n快逃RRRRR");
        }
        if(current_PM >= 85 && current_PM < 100){
            image.setImageDrawable(getResources().getDrawable(R.drawable.a10));
            text_air.setText("中華民國\n大陸淪陷重災區\n的對岸非法政權表示\n霧霾對積光武器\n就是最好的防禦");
        }
        if(current_PM >= 100){
            image.setImageDrawable(getResources().getDrawable(R.drawable.a11));
            text_air.setText("傳說中的\n★☆✡黑爆✡☆★\n共匪政府表示\n他不存在\n所以是★☆✡白色✡☆★");
        }
    }
}
