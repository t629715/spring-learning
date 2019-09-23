package learning.spring.mvc.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import java.util.Properties;

public class DispatcherServlet extends HttpServlet {

    private static final String LOCATION = "contextConfigLocation";

    private Properties properties = new Properties();
    /**
     * 初始化、加载配置文件
     */
    public  void init(ServletConfig config){
        //加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));
        //扫描所有相关的类
        doScanner(properties.get("scannerPackage"));
        //初始化所有相关的类，并保存到IOC容器中
        doInstance();
        //依赖注入
        doAutowired();
        //构造HandlerMapping
        initHandlerMapping();
        //提示请求信息
        System.out.println("init success");
    }
    private void doLoadConfig(String initParameter) {

    }
    private void doScanner(Object scannerPackage) {
    }
    private void doInstance() {
    }

    private void doAutowired() {
    }
    private void initHandlerMapping() {
    }
}
