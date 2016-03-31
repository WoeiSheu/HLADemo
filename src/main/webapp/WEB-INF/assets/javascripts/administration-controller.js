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

    $scope.update = function (federate) {

    };

    $scope.delete = function(federate) {
        $http({method: "Delete", url: "/federates/" + federate.federation + "/" + federate.name }).success(function(data) {
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
        $interval(function () {
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
                            "time": federates[federate].time
                        };
                        $scope.federates.push(ctrl.federate);
                    }
                }
            });
        },2000);
    };
    this.updateFederates();
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
