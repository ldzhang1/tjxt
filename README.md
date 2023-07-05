# 天机学堂
### 线上职业培训项目-----仿中国大学MOOC网(慕课)
1.main分支中主要是开发项目过程中遇到的bug以及解决办法
2.主要代码在lesson-init分支

#### 实现效果
- 学生端首页
  - 购买课程
  - 课程播放等
  ![Uploading image.png…]()

- 后台管理端
  ![Uploading image.png…]()
  
#### 一、项目架构
![image](https://github.com/ldzhang1/tjxt/assets/104254485/e626e641-1aae-49fa-acd6-1cbfdb72ba43)

#### 二、技术架构
![image](https://github.com/ldzhang1/tjxt/assets/104254485/9c0d50ac-0f71-4986-99a9-4aee65addf18)

#### 三、功能分析
天机学堂分为两部分：
- 学生端：其核心业务主体就是学员，所有业务围绕着学员的展开
- 管理端：其核心业务主体包括老师、管理员、其他员工，核心业务围绕着老师展开

#### 四、核心业务流程
##### 4.1 教师端的核心业务流程有
- 课程分类管理：课程分类的增删改查
- 媒资管理：媒资的增删改查、媒资审核
- 题目管理：试题的增删改查、试题批阅、审核
- 课程管理：课程增删改查、课程上下架、课程审核、发布等等
![image](https://github.com/ldzhang1/tjxt/assets/104254485/df4df910-a517-44db-b55c-642d777c8c9a)

##### 4.2 学生端的核心业务流程有
- 学员的核心业务就是买课、学习，基本流程如下：
![image](https://github.com/ldzhang1/tjxt/assets/104254485/71f624da-d3eb-4fda-9ebb-4a002014da0f)

