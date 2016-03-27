<%--
  Created by IntelliJ IDEA.
  User: Hypocrisy
  Date: 3/23/2016
  Time: 2:17 PM
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
  <meta charset="utf-8">
  <meta http-equiv="X-UA-Compatible" content="IE=edge">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>HLA Demo</title>
  <link rel="shortcut icon" href="assets/images/favicon.ico">
  <link rel="stylesheet" href="assets/stylesheets/bootstrap.css">
  <link rel="stylesheet" href="assets/stylesheets/bootstrap-theme.css">
  <link rel="stylesheet" href="assets/stylesheets/home.css">
</head>
<body ng-app="HLADemo">
  <nav class="navbar navbar-inverse navbar-static-top">
    <div class="container">
      <div class="navbar-header">
        <a class="navbar-brand" href="#">HLA Demo</a>
        <button type="button" class="btn btn-navbar" data-toggle="collapse" data-target=".navbar-collapse">
          <span class="icon-bar"></span>
          <span class="icon-bar"></span>
          <span class="icon-bar"></span>
        </button>
      </div>

      <div class="navbar-collapse collapse">
        <ul class="nav navbar-nav">
          <li class="active"><a href="#">Home</a></li>
          <li id="rti"><a href="#/rti">RTI</a></li>
          <li id="federate"><a href="#/federates">Federates</a></li>
          <li id="administration"><a href="#/administration">Admin</a></li>
        </ul>
      </div>
    </div>
  </nav>
  <div class="container-fluid" ng-controller="GlobalController as globalCtrl">
    <div class="row">
      <span class="col-xs-2"><img src="assets/images/logo.png"></span>
      <span class="col-xs-8 text-center" style="font-size:36px">HLA Demo</span>
      <span class="col-xs-2" style="height:50px;"></span>
    </div>
    <br>

    <div ng-view>
    </div>
  </div>
<script src="assets/javascripts/jquery-2.1.1.js"></script>
<script src="assets/javascripts/bootstrap.js"></script>
<script src="assets/javascripts/angular.min.js"></script>
<script src="assets/javascripts/angular-route.min.js"></script>
<script src="assets/javascripts/angular-cookies.min.js"></script>
<script src="assets/javascripts/angular-sanitize.min.js"></script>
<script src="assets/javascripts/app.js"></script>
<script src="assets/javascripts/routes.js"></script>
<script src="assets/javascripts/global-controller.js"></script>
<script src="assets/javascripts/federates-controller.js"></script>
<script src="assets/javascripts/rti-controller.js"></script>
<script src="assets/javascripts/administration-controller.js"></script>
</body>
</html>
