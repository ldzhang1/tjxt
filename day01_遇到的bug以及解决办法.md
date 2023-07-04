# 天机学堂项目bug解决

### 1. 启动项目时报错：

*Connection reset and 'parent.relativePath' points at wrong local POM @ line 32, column 13 -> [Help 2]*

![image-20230703222106584](images/image-20230703222106584.png)

#### 1. 解决办法：

[(67条消息) Maven install报错:Non-resolvable parent POM for..._你知道爬上树的感觉吗的博客-CSDN博客](https://blog.csdn.net/qq_40306266/article/details/115766704)

![image-20230703224054275](images\image-20230703224054275.png)

![image-20230703224221455](images\image-20230703224221455.png)

### 2. Maven的版本导致报错

![image-20230703214407829](images\image-20230703214407829.png)

![image-20230703214547240](images\image-20230703214547240.png)



![image-20230704094754713](images\image-20230704094754713.png)

![image-20230704094916720](images\image-20230704094916720.png)

### 3. 解决bug后idea不能push代码到gogs，报错如下：

*error: RPC failed; HTTP 401 curl 22 The requested URL returned error: 401*
*fatal: The remote end hung up unexpectedly*

#### 解决办法

![image-20230704214646637](images\image-20230704214646637.png)
