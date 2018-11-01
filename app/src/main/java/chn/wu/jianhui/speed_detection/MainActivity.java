package chn.wu.jianhui.speed_detection;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.track.AMapTrackClient;
import com.amap.api.track.ErrorCode;
import com.amap.api.track.OnTrackLifecycleListener;
import com.amap.api.track.TrackParam;
import com.amap.api.track.query.entity.LocationMode;
import com.amap.api.track.query.model.AddTerminalRequest;
import com.amap.api.track.query.model.AddTerminalResponse;
import com.amap.api.track.query.model.AddTrackRequest;
import com.amap.api.track.query.model.AddTrackResponse;
import com.amap.api.track.query.model.DistanceRequest;
import com.amap.api.track.query.model.DistanceResponse;
import com.amap.api.track.query.model.HistoryTrackResponse;
import com.amap.api.track.query.model.LatestPointResponse;
import com.amap.api.track.query.model.OnTrackListener;
import com.amap.api.track.query.model.ParamErrorResponse;
import com.amap.api.track.query.model.QueryTerminalRequest;
import com.amap.api.track.query.model.QueryTerminalResponse;
import com.amap.api.track.query.model.QueryTrackResponse;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import chn.wu.jianhui.speed_detection.utils.AmapServiceUtils;
import chn.wu.jianhui.speed_detection.view.SpeedView;


/*
* @author W.J.H
* @email jianhui.wu.chn@hotmail.com
* @create at 2018/10/27
*/
public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID_SERVICE_RUNNING = "CHANNEL_ID_SERVICE_RUNNING";
    private int[] item_icon = new int[]{R.drawable.start, R.drawable.highspeed, R.drawable.avgspeed, R.drawable.high, R.drawable.accuracy, R.drawable.site, R.drawable.direction};
    private String[] item_name = new String[]{"开始时间", "最高速度", "平均速度", "海拔高度", "位置精度", "位置信息", "当前方向"};
    private String[] item_content = new String[]{"--:--:--", "0.00 KM/H", "0.00 KM/H", "0 M", "0 M", "00.0000° 00.0000°",  "0.00°"};

    private MyBaseAdapter mAdapter = new MyBaseAdapter();
    private ListView listView;
    private SpeedView speedView;
    private ProgressBar progressBar;
    private TextView status_t;
    private TextView time_t;
    private TextView distance_t;
    private Button track_btn;
    private double distance;
    private double max_speed;
    private Date start_time = new Date();
    private double avg_speed;
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
    private DecimalFormat decimalFormat = new DecimalFormat("##0.00");
    private DecimalFormat decimalFormat_direction = new DecimalFormat("##0.0000");
    /*************************定位服务********************************/
    //定位服务类。此类提供单次定位、持续定位、地理围栏、最后位置相关功能
    //声明mlocationClient对象
    private AMapLocationClient mlocationClient;
    //声明mLocationOption对象
    private AMapLocationClientOption mLocationOption = null;
    //位置变化监听器
    private AMapLocationListener aMapLocationListener = new AMapLocationListener() {
        @Override
        public void onLocationChanged(AMapLocation aMapLocation) {

            if (aMapLocation != null)
            {
                if (aMapLocation.getErrorCode() == 0)
                {
                    double speed = aMapLocation.getSpeed() * 60 * 60 / 1000 ; // m/s 2 km/s
                    max_speed = max_speed < speed ? speed : max_speed;
                    avg_speed = distance / ((System.currentTimeMillis() - start_time.getTime())/1000.0/60/60);
                    item_content[0] = simpleDateFormat.format(start_time);
                    item_content[1] = decimalFormat.format(max_speed) + " KM/H";
                    item_content[2] = decimalFormat.format(max_speed) + " KM/H";
                    item_content[3] = aMapLocation.getAltitude() + " M";
                    item_content[4] = aMapLocation.getAccuracy() + " M";
                    item_content[5] = decimalFormat_direction.format(aMapLocation.getLatitude()) + "°  " + decimalFormat_direction.format(aMapLocation.getLongitude()) + "°";
                    item_content[6] = simpleDateFormat.format(aMapLocation.getBearing()) + " M";
                    listView.setAdapter(mAdapter);
                    time_t.setText(simpleDateFormat.format(new Date(System.currentTimeMillis() - start_time.getTime() - 8*60*60*1000)));
                    distance_t.setText(decimalFormat.format(distance) + " KM");
                    progressBar.setProgress(aMapLocation.getSatellites());
                    status_t.setText("运行中");
                    speedView.setStatues(speed, aMapLocation.getBearing());
                    aMapTrackClient.queryDistance(new DistanceRequest(serviceId, terminalId, start_time.getTime(), System.currentTimeMillis(), trackId), onTrackListener);
                }
                else
                {
                    status_t.setText("定位信号差");
                    progressBar.setProgress(0);
                    speedView.setStatues(0, 0);

                    Toast.makeText(MainActivity.this,"AmapError"
                            + "location Error, ErrCode:" + aMapLocation.getErrorCode() + ", errInfo:" + aMapLocation.getErrorInfo(), Toast.LENGTH_LONG).show();
                    //显示错误信息ErrCode是错误码，errInfo是错误信息，详见错误码表。
                    Log.e("AmapError","location Error, ErrCode:"
                            + aMapLocation.getErrorCode() + ", errInfo:"
                            + aMapLocation.getErrorInfo());
                }
            }
        }
    };

    /*************************轨迹服务********************************/
    //初始化猎鹰sdk服务类
    private AMapTrackClient aMapTrackClient;
    // 创建的服务id
    private long serviceId;
    // 唯一标识某个用户或某台设备的名称
    private String terminalName;
    private long terminalId;
    //服务key
    private String key;
    //trackId
    private long trackId;
    //猎鹰服务请求根据
    private AmapServiceUtils amapServiceUtils;

    //猎鹰sdk服务启停状态的监听器
    private final OnTrackLifecycleListener onTrackLifecycleListener = new OnTrackLifecycleListener() {
        @Override
        public void onBindServiceCallback(int i, String s) {

        }

        @Override
        public void onStartGatherCallback(int status, String msg) {
            if (status == ErrorCode.TrackListen.START_GATHER_SUCEE || status == ErrorCode.TrackListen.START_GATHER_ALREADY_STARTED) {
                Log.v("gdsdk",status + " " + msg);
            } else {
                Log.v("gdsdk",status + " " + msg);
                status_t.setText("定位信号差");
                Toast.makeText(MainActivity.this, "网络差，定位采集启动失败", Toast.LENGTH_SHORT);
            }
        }

        @Override
        public void onStartTrackCallback(int status, String msg) {
            if (status == ErrorCode.TrackListen.START_TRACK_SUCEE ||
                    status == ErrorCode.TrackListen.START_TRACK_SUCEE_NO_NETWORK ||
                    status == ErrorCode.TrackListen.START_TRACK_ALREADY_STARTED) {
                // 服务启动成功，继续开启收集上报
                Log.v("gdsdk",status + " " + msg);
                aMapTrackClient.startGather(this);
            } else {
                Log.v("gdsdk",status + " " + msg);
                status_t.setText("定位信号差");
                Toast.makeText(MainActivity.this, "网络差，轨迹上报服务服务启动失败", Toast.LENGTH_SHORT);
            }
        }

        @Override
        public void onStopGatherCallback(int i, String s) {

        }

        @Override
        public void onStopTrackCallback(int i, String s) {

        }
    };

    private OnTrackListener onTrackListener = new OnTrackListener() {
        @Override
        public void onQueryTerminalCallback(QueryTerminalResponse queryTerminalResponse) {
            if (queryTerminalResponse.isSuccess()) {
                if (queryTerminalResponse.getTid() <= 0) {
                    // terminal还不存在，先创建
                    aMapTrackClient.addTerminal(new AddTerminalRequest(terminalName, serviceId), onTrackListener);
                }
                else {
                    // terminal已经存在，直接开启猎鹰服务
                    terminalId = queryTerminalResponse.getTid();
                    aMapTrackClient.addTrack(new AddTrackRequest(serviceId, terminalId), onTrackListener);
                }
            }
            else
            {
                // 请求失败
                Toast.makeText(MainActivity.this, "请求失败，" + queryTerminalResponse.getErrorMsg(), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onCreateTerminalCallback(AddTerminalResponse addTerminalResponse) {
            if (addTerminalResponse.isSuccess()) {
                // 创建完成，开启猎鹰服务
                terminalId = addTerminalResponse.getTid();
                aMapTrackClient.addTrack(new AddTrackRequest(serviceId, terminalId), onTrackListener);
            } else {
                // 请求失败
                Toast.makeText(MainActivity.this, "请求失败，" + addTerminalResponse.getErrorMsg(), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onDistanceCallback(DistanceResponse distanceResponse) {
            distance = distanceResponse.getDistance() / 1000; //m 2 km
        }

        @Override
        public void onLatestPointCallback(LatestPointResponse latestPointResponse) {

        }

        @Override
        public void onHistoryTrackCallback(HistoryTrackResponse historyTrackResponse) {

        }

        @Override
        public void onQueryTrackCallback(QueryTrackResponse queryTrackResponse) {

        }

        @Override
        public void onAddTrackCallback(AddTrackResponse addTrackResponse) {
            if (addTrackResponse.isSuccess()) {
                trackId = addTrackResponse.getTrid();
                TrackParam param = new TrackParam(serviceId, terminalId);
                param.setTrackId(trackId);
                aMapTrackClient.startTrack(param, onTrackLifecycleListener);
            }
            else {
                Toast.makeText(MainActivity.this, "网络请求失败，" + addTrackResponse.getErrorMsg(), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onParamErrorCallback(ParamErrorResponse paramErrorResponse) {

        }
    };

    //消息监听
    private Handler handler = new Handler()
    {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == AmapServiceUtils.SUCCESS)
            {
                serviceId = msg.getData().getInt("serviceId");
                startTrackClient();
            }
            else if(msg.what == AmapServiceUtils.ERROR)
            {
                Toast.makeText(MainActivity.this, "请求服务失败", Toast.LENGTH_SHORT).show();
            }
        }
    };


    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //透明状态栏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        //透明导航栏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        listView = findViewById(R.id.listview);
        status_t = findViewById(R.id.status);
        speedView = findViewById(R.id.speed_view);
        progressBar = findViewById(R.id.gpsProgressBar);
        distance_t = findViewById(R.id.distance);
        time_t = findViewById(R.id.time);
        track_btn = findViewById(R.id.trackbtn);
        track_btn.setTypeface(Typeface.createFromAsset(getAssets(),"fonts/iconfont.ttf"));
        /********************************定位服务**************************************/
        mlocationClient = new AMapLocationClient(getApplicationContext());
        mlocationClient.enableBackgroundLocation(2, createNotification());
        //初始化定位参数
        mLocationOption = new AMapLocationClientOption();
        //设置定位监听
        mlocationClient.setLocationListener(aMapLocationListener);
        //设置定位模式为高精度模式，Battery_Saving为低功耗模式，Device_Sensors是仅设备模式
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Device_Sensors);
        //设置定位间隔,单位毫秒,默认为2000ms
        mLocationOption.setInterval(1000);
        //设置定位参数
        mlocationClient.setLocationOption(mLocationOption);
        mlocationClient.startLocation();

        /*******************************轨迹服务***************************************/
        terminalName = ((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
        //获取猎鹰接口的key
        try
        {
            key = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA).metaData.getString("com.amap.api.v2.webapikey");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        amapServiceUtils = new AmapServiceUtils(handler);
        //配置猎鹰sdk
        aMapTrackClient = new AMapTrackClient(getApplicationContext());
        //设置定位周期，和轨迹上报周期
        aMapTrackClient.setInterval(1, 30);
        //仅适用gps
        aMapTrackClient.setLocationMode(LocationMode.DEVICE_SENSORS);
        //开启服务
        amapServiceUtils.searchService(key);

        listView.setAdapter(mAdapter);
    }

    //启动上报
    public void startTrackClient()
    {
        aMapTrackClient.queryTerminal(new QueryTerminalRequest(serviceId, terminalName), onTrackListener);
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        aMapTrackClient.stopGather(onTrackLifecycleListener);
        TrackParam params = new TrackParam(serviceId, terminalId);
        params.setTrackId(trackId);
        aMapTrackClient.stopTrack(params, onTrackLifecycleListener);
        mlocationClient.stopLocation();
        mlocationClient.onDestroy();
    }

    /**
     * 由于猎鹰sdk在运行时需要频繁进行定位，而从Android 8.0开始系统为实现降低功耗，对后台应用获取用户位置信息频率进行了限制，每小时只允许更新几次位置信息，详细信息请参考官方说明。按照官方指引，如果要提高位置更新频率，需要后台应用提供一个前台服务通知告知。
     * 猎鹰sdk提供了一个接口，只需调用该接口即可在轨迹上报服务启动时为您的应用创建一个前台服务通知，轨迹上报服务停止后，该通知会自动消失。这样，当您的应用切换到后台后仍然有一个前台服务通知存在，以此规避Android8.0对后台定位的限制。
     * @return
     */
    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_SERVICE_RUNNING, "app service", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
            builder = new Notification.Builder(getApplicationContext(), CHANNEL_ID_SERVICE_RUNNING);
        } else {
            builder = new Notification.Builder(getApplicationContext());
        }
        Intent nfIntent = new Intent(MainActivity.this, MainActivity.class);
        nfIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        builder.setContentIntent(PendingIntent.getActivity(MainActivity.this, 0, nfIntent, 0))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("行车辅助")
                .setContentText("车辅助运行中");
        Notification notification = builder.build();
        return notification;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if(keyCode==KeyEvent.KEYCODE_BACK)
        {
            new AlertDialog.Builder(this).setTitle("提示").setMessage("您确定退出？").setPositiveButton("确定", (i, d)->{
                MainActivity.this.finish();
            }).setNegativeButton("取消", null).create().show();
        }
        return super.onKeyDown(keyCode, event);
    }


    class MyBaseAdapter extends BaseAdapter {
        //得到item的总数
        @Override
        public int getCount() {
            //返回ListView Item条目的总数
            return item_name.length;
        }
        //得到Item代表的对象
        @Override
        public Object getItem(int position) {
            //返回ListView Item条目代表的对象
            return item_name[position];
        }
        //得到Item的id
        @Override
        public long getItemId(int position) {
            //返回ListView Item的id
            return position;
        }
        //得到Item的View视图
        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            ViewHolder holder;
            if (convertView == null)
            {
                convertView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.listviewitem,parent,false);
                holder = new ViewHolder();
                holder.icon = (ImageView)convertView.findViewById(R.id.icon);
                holder.name = (TextView) convertView.findViewById(R.id.name);
                holder.content = (TextView) convertView.findViewById(R.id.content);
                convertView.setTag(holder);
            }
            else
            {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.icon.setBackgroundResource(item_icon[position]);
            holder.name.setText(item_name[position]);
            holder.content.setText(item_content[position]);
            return convertView;
        }

        class ViewHolder
        {
            private ImageView icon;
            private TextView name;
            private TextView content;
        }
    }
}