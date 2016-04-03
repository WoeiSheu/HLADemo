/**
 * Created by Hypocrisy on 3/24/2016.
 * This controller manage the federates.
 */
angular.module('HLADemo').controller('AdministrationController', ['$http', '$scope', '$interval', function($http, $scope, $interval) {
    var ctrl = this;
    $scope.request = {"crcAddress":"localhost", "federationName": "", "federateName": "", "strategy": "Regulating" };
    console.log(JSON.stringify($scope.request));

    $scope.create = function() {
        $http({method: "POST", url: "/federates", data: JSON.stringify($scope.request)}).success(function(data) {
            console.log(data);
            $("#createFederate").modal('hide');
            ctrl.updateFederates();
        });
    };

    $scope.cancel = function () {
    };

    $scope.run = function (federate) {
        if(federate.startOrPause == 'Start') {
            $http({method: "POST", url: "/federates/start", data: JSON.stringify($scope.request)}).success(function(data){
                console.log(data);
                federate.startOrPause = 'Pause';
            });
        } else if(federate.startOrPause = 'Pause') {
            $http({method: "POST", url: "/federates/pause", data: JSON.stringify($scope.request)}).success(function (data) {
               console.log(data);
                federate.startOrPause = 'Start';
            });
        }
    };

    $scope.delete = function(federate) {
        $http({method: "DELETE", url: "/federates/" + federate.federation + "/" + federate.name }).success(function(data) {
            console.log(data);
            ctrl.updateFederates();
        });
    };

    $scope.clickToUpload = function(src) {
        $scope.source = src;
        document.getElementById('fileToUpload').click();
    };

    $scope.upload = function (files) {
        //$scope.request.fomFile = files[0];
    };

    $scope.availableStrategies = ["Regulating","Constrained","Regulating and Constrained"];

    this.updateFederates = function () {
        this.federate = {"name": "", "federation": "", "fom": "", "strategy": "", "time": ""};
        $scope.federates = [];
        $http({method: 'GET', url: '/federates'}).success(function (data) {
            for (var federation in data) {
                var federates = data[federation]
                for (var federate in federates) {
                    ctrl.federate = {
                        "name": federate,
                        "federation": federation,
                        "fom": "",
                        "strategy": federates[federate].strategy,
                        "time": federates[federate].time,
                        "startOrPause": "Start"
                    };
                    $scope.federates.push(ctrl.federate);
                }
            }
        });
    };
    this.updateFederates();

    $interval(function () {
        $scope.federates.map(function(item,i,s){
            $http({method: "GET", url: "/federates/time/" + item.federation + "/" + item.name}).success(function (data) {
                item.time = data;
                s[i] = item;
            });
        });
    }, 2000);
}])
.directive('createFederate', ['$http', '$timeout', '$location', function() {
    return {
        restrict: 'E', // E = Element, A = Attribute, C = Class, M = Comment
        templateUrl: 'angularjs/create-federate.html',
        link: function(scope, iElm, iAttrs, controller) {
            $("#createFederate").draggable({});
        }
   };
}]);
