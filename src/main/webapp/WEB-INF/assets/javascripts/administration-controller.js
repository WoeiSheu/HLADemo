/**
 * Created by Hypocrisy on 3/24/2016.
 * This controller manage the federates.
 */
angular.module('HLADemo').controller('AdministrationController', ['$http', '$scope', '$interval', function($http, $scope, $interval) {
    var ctrl = this;
    $scope.request = {"crcAddress":"localhost", "federationName": "", "federateName": "", "fomUrl": "http://localhost:8080/assets/config/HLADemo.xml", "strategy": "Regulating", "step": "", "lookahead": ""};
    console.log(JSON.stringify($scope.request));

    $scope.create = function() {
        $http({method: "POST", url: "/federates", data: JSON.stringify($scope.request)}).success(function(data) {
            console.log(data);
            $("#createFederate").modal('hide');
            //ctrl.updateFederates();
            var federate = {
                "name": $scope.request.federateName,
                "federation": $scope.request.federationName,
                "fom": "",
                "strategy": $scope.request.strategy,
                "time": 0,
                "step": $scope.request.step,
                "lookahead": $scope.request.lookahead,
                "startOrPause": "Start"
            };
            $scope.federates.push(federate);
        });
    };

    $scope.cancel = function () {
    };

    $scope.edit = function(federate) {
        ctrl.federate = federate;
        //ctrl.backup_federate = $.extend(true, {}, federate);
        $scope.updateInfo = {
            "strategy": federate.strategy,
            "step": federate.step,
            "lookahead": federate.lookahead
        };
    };
    $scope.update = function (federate) {
        $http({method: "PUT", url: "/federates/update/" + federate.federation + "/" + federate.name, data: $scope.updateInfo}).success(function (data) {
            ctrl.federate.strategy = $scope.updateInfo.strategy;
            ctrl.federate.step = $scope.updateInfo.step;
            ctrl.federate.lookahead = $scope.updateInfo.lookahead;
        });
        $("#editFederate").modal("hide");
    };

    $scope.run = function (federate) {
        if(federate.startOrPause == 'Start') {
            $http({method: "PUT", url: "/federates/start/" + federate.federation + "/" + federate.name}).success(function(data){
                federate.startOrPause = 'Pause';
            });
        } else if(federate.startOrPause == 'Pause') {
            $http({method: "PUT", url: "/federates/pause/" + federate.federation + "/" + federate.name}).success(function (data) {
                federate.startOrPause = 'Start';
            });
        }
    };

    $scope.delete = function(federate) {
        $http({method: "DELETE", url: "/federates/" + federate.federation + "/" + federate.name }).success(function(data) {
            console.log(data);
            //ctrl.updateFederates();
            $scope.federates = $scope.federates.filter(function(item) {
                return !(item.federation == federate.federation && item.name == federate.name);
            });
        });
    };

    $scope.clickToUpload = function(src) {
        $scope.source = src;
        document.getElementById('fileToUpload').click();
    };

    $scope.upload = function (files) {
        //$scope.request.fomFile = files[0];
        var fd = new FormData();
        fd.append("file", files[0]);
        var url;
        if($scope.source == 'fom') {
            url = "/federates/fomFile";
        } else {
            return;
        }

        $http({method: "POST", url: url, headers: {'Content-Type': undefined}, transformRequest: angular.identity, data: fd}).success(function (data) {
            //console.log(data);
            $scope.request.fomUrl = data.url;
        });
    };

    $scope.availableStrategies = ["Neither Regulating nor Constrained","Regulating","Constrained","Regulating and Constrained"];

    /**********************
     * Init or reset federates.
     **********************/
    this.initFederates = function () {
        $scope.federates = [];
        $http({method: 'GET', url: '/federates'}).success(function (data) {
            for (var federation in data) {
                var federates = data[federation];
                for (var federate in federates) {
                    var item = {
                        "name": federate,
                        "federation": federation,
                        "fom": "",
                        "strategy": federates[federate].strategy,
                        "time": federates[federate].time,
                        "step": federates[federate].step,
                        "lookahead": federates[federate].lookahead
                    };
                    federates[federate].status ? item.startOrPause = "Pause" : item.startOrPause = "Start";
                    $scope.federates.push(item);
                }
            }
        });
    };
    this.initFederates();

    $interval(function () {
        $scope.federates.forEach(function(item,i,s){
            $http({method: "GET", url: "/federates/time/" + item.federation + "/" + item.name}).success(function (data) {
                item.time = data.toFixed(2);
                s[i] = item;
            });
        });
    }, 1000);
}])
.directive('createFederate', ['$http', '$timeout', '$location', function() {
    return {
        restrict: 'E', // E = Element, A = Attribute, C = Class, M = Comment
        templateUrl: 'angularjs/create-federate.html',
        link: function(scope, iElm, iAttrs, controller) {
            $("#createFederate").draggable({});
        }
   };
}])
.directive('editFederate', ['$http', '$timeout', '$location', function() {
    return {
        restrict: 'E', // E = Element, A = Attribute, C = Class, M = Comment
        templateUrl: 'angularjs/edit-federate.html',
        link: function(scope, iElm, iAttrs, controller) {
            $("#editFederate").draggable({});
        }
    };
}]);
