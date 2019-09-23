package com.learning.spring.mvc.servlet;


import com.learning.spring.mvc.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
public class DispatcherServlet extends HttpServlet {

    private static final String LOCATION = "configLocation";
    private static final String SCANNER_PATH = "scannerPackage";

    /**
     * 存储扫描到的类
     */
    private List<String> classNames = new ArrayList<>();
    /**
     * 保存 application.properties 的配置内容
     */
    private Properties properties = new Properties();

    private Map<String, Object> ioc = new HashMap<>();
    /**
     * 保存controller中所有Mapping的映射关系
     */
    private Map<String, Method> handlerMapping = new HashMap();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //派遣分发任务
        //委派模式
        try {
            doDispatch(req, resp);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        //获取Url
        String url = req.getRequestURI();
        //获取上下文配置
        String contextPath =req.getContextPath();
        url = url.replaceAll(contextPath,"").replaceAll("/+","/");
        //判断地址是否存在  handlerMapping 容器中获取，若为空则不存在
        if (!handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found ");
            return;
        }
        //获取请求的方法
        Method method = this.handlerMapping.get("url");
        //第一个参数，方法所在的实例
        //第二个参数，调用时所需要的实参
        Map<String, String[]> paramMap = req.getParameterMap();
        //获取形参列表
        Class<?>[] paramTypes = method.getParameterTypes();
        //保存赋值参数的位置
        Object[] paramValues = new Object[paramTypes.length];
        //遍历  根据参数位置动态赋值
        for (int i=0; i<paramTypes.length; i++){
            Class<?> paramType = paramTypes[i];
            //判断是否时 请求参数
            if (paramType == HttpServletRequest.class){
                paramValues[i] = req;
                continue;
            }else if (paramType == HttpServletResponse.class){//判断是否是 响应参数
                paramValues[i] = resp;
                continue;
            }else if (paramType == String.class){
                //提取方法中加了注解的参数
                Annotation[][] annotations = method.getParameterAnnotations();
                for(int j=0; j<annotations.length; j++){
                    for (Annotation annotation:annotations[i]){
                        if (annotation instanceof RequestParam){
                            String paramName = ((RequestParam) annotation).value();
                            if (!"".equals(paramName.trim())){
                                String value = Arrays.toString(paramMap.get(paramName))
                                        .replaceAll("\\[|\\]","")
                                        .replaceAll("\\s","");
                                paramValues[i] = value;
                            }
                        }
                    }
                }

            }
        }

        String beanName = toLowerFirst(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName),new Object[]{req, resp, paramMap.get("name")[0]});

    }

    private String toLowerFirst(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] = String.valueOf(chars[0]).toLowerCase().toCharArray()[0];
        return String.valueOf(chars);
    }

    /**
     * 初始化、加载配置文件
     */
    public  void init(ServletConfig config){
        //加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));
        //扫描所有相关的类
        doScanner(properties.get(SCANNER_PATH).toString());
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
        InputStream is = null;
        is = this.getClass().getClassLoader().getResourceAsStream(initParameter);
        //读取配置文件
        try {
            properties.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private void doScanner(String scannerPackage) {
        //获取并转换扫描路径
        URL url = this.getClass().getClassLoader().getResource("/" + scannerPackage.replaceAll("\\.","/"));
        File classPath =new File(url.getFile());
        for (File file:classPath.listFiles()){
            //判断是否是文件夹
            if (file.isDirectory()){
                doScanner(scannerPackage + "." + file.getName());
            }else {
                if (!file.getName().equals(".class")) continue;
                String className = scannerPackage + "." + file.getName().replace(".class","");
                classNames.add(className);
            }
        }
    }

    /**
     * 控制反转
     * 工厂模式实现
     */
    private void doInstance() {
        //判断classNames是否为空
        if (classNames.isEmpty()) return;
        try{
            //判断是否被Controller注解修饰
            for (String className:classNames){
                //根据全类名 获取对象
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Controller.class)){
                    Object instance = clazz.newInstance();
                    String beanName = toLowerFirst(clazz.getSimpleName());
                    ioc.put(beanName,instance);
                }else if (clazz.isAnnotationPresent(Service.class)){
                    String beanName = clazz.getSimpleName();
                    beanName = toLowerFirst(beanName);
                    Service service = clazz.getAnnotation(Service.class);
                    if (!"".equals(service.value())){
                        beanName = service.value();
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    //3、根据类型注入实现类，投机取巧的方式
                    for (Class<?> i : clazz.getInterfaces()) {
                        if(ioc.containsKey(i.getName())){
                            throw new Exception("The beanName is exists!!");
                        }
                        ioc.put(i.getName(),instance);
                    }
                }else {
                    continue;
                }

            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private void initHandlerMapping() {
        if(ioc.isEmpty()){ return; }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(Controller.class)){ continue; }

            String baseUrl = "";
            //获取Controller的url配置
            if(clazz.isAnnotationPresent(RequestMapping.class)){
                RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
                baseUrl = requestMapping.value();
            }

            //获取Method的url配置
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {

                //没有加RequestMapping注解的直接忽略
                if(!method.isAnnotationPresent(RequestMapping.class)){ continue; }

                //映射URL
                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                //  /demo/query

                //  (//demo//query)

                String url = ("/" + baseUrl + "/" + requestMapping.value())
                        .replaceAll("/+", "/");
                handlerMapping.put(url,method);
                System.out.println("Mapped " + url + "," + method);
            }
        }


    }

    private void doAutowired() {
        if(ioc.isEmpty()){ return; }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //拿到实例对象中的所有属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if(!field.isAnnotationPresent(Autowired.class)){ continue; }
                Autowired autowired = field.getAnnotation(Autowired.class);
                String beanName = autowired.value().trim();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }
                //不管你愿不愿意，强吻
                field.setAccessible(true); //设置私有属性的访问权限
                try {
                    //执行注入动作
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (Exception e) {
                    e.printStackTrace();
                    continue ;
                }
            }
        }
    }
}
