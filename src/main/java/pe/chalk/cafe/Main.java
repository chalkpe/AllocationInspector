package pe.chalk.cafe;

import org.json.JSONObject;
import pe.chalk.takoyaki.Takoyaki;
import pe.chalk.takoyaki.Target;
import pe.chalk.takoyaki.utils.TextFormat;
import pe.chalk.test.Staff;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author ChalkPE <chalkpe@gmail.com>
 * @since 2015-10-10
 */
public class Main {
    public static Staff staff;
    public static Takoyaki takoyaki;
    public static List<AllocationInspector> inspectors;

    public static boolean DELAY = true;
    public static String html;
    public static Path htmlOutput;

    public static final PrintStream realErr = System.err;
    public static final PrintStream fakeErr = new PrintStream(new OutputStream(){
        @Override
        public void write(int b) throws IOException{
            //DO NOTHING
        }
    });

    public static void main(String[] args) throws IOException, InterruptedException {
        Main.takoyaki = new Takoyaki();

        try{
            Properties accountProperties = new Properties(); accountProperties.load(new FileInputStream("account.properties"));
            Takoyaki.getInstance().getLogger().info("네이버에 로그인합니다: " + accountProperties.getProperty("user.id"));

            System.setErr(Main.fakeErr);

            Main.staff = new Staff(null, accountProperties);
            Main.staff.getOptions().setJavaScriptEnabled(false);

            System.setErr(Main.realErr);
        }catch(IllegalStateException e){
            Takoyaki.getInstance().getLogger().critical("네이버에 로그인할 수 없습니다!");
            return;
        }

        Path propertiesPath = Paths.get("AllocationInspector.json");
        if(Files.notExists(propertiesPath)){
            return;
        }

        JSONObject properties = new JSONObject(new String(Files.readAllBytes(propertiesPath), StandardCharsets.UTF_8));

        try{
            Main.html = new String(Files.readAllBytes(Paths.get(properties.getString("htmlInput"))), StandardCharsets.UTF_8);
            Main.htmlOutput = Paths.get(properties.getString("htmlOutput"));
        }catch(IOException e){
            e.printStackTrace();
        }

        Main.inspectors = Takoyaki.<JSONObject>buildStream(properties.getJSONArray("targets")).map(AllocationInspector::new).collect(Collectors.toList());
        Main.inspectors.forEach(inspector -> {
            Target target = Takoyaki.getInstance().getTarget(inspector.getClubId());
            Takoyaki.getInstance().getLogger().info("게시글을 검사합니다: 대상자 " + inspector.getAssignees().size() + "명: " + target.getName() + " (ID: " + target.getClubId() + ")");
        });

        new Thread(() -> {
            try{
                Thread.sleep(1000 * 60 * 30); //30m

                MemberArticle.cache.clear();
                Takoyaki.getInstance().getLogger().notice("CACHE CLEARED!");
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }).start();

        //noinspection InfiniteLoopStatement
        while(true){
            try{
                Calendar calendar = Calendar.getInstance(Locale.KOREA);
                Date today = calendar.getTime();

                calendar.add(Calendar.DATE, -1);
                Date yesterday = calendar.getTime();

                Main.inspect(today, yesterday);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    public static void inspect(Date... dates) throws InterruptedException {
        String result = String.join(String.format("%n"), Stream.of(dates).flatMap(date -> Main.inspectors.stream().map(inspector -> inspector.inspect(date))).collect(Collectors.toList()));

        Takoyaki.getInstance().getLogger().info(result);
        Main.html(result);
    }

    public static void html(String messages){
        try{
            Files.write(Main.htmlOutput, String.format(Main.html, TextFormat.replaceTo(TextFormat.Type.HTML, messages)).getBytes(StandardCharsets.UTF_8));
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static boolean delay(long millis){
        if(!Main.DELAY) return false;

        try{
            Thread.sleep(millis);
            return true;
        }catch(InterruptedException e){
            e.printStackTrace();
            return false;
        }
    }
}
