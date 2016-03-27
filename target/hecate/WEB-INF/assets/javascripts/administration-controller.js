/**
 * Created by Gaea on 3/24/2016.
 */
angular.module('HLADemo').controller('AdministrationController', ['$http', '$scope', '$location', function($http, $scope, $location) {
    var ctrl = this;
    $scope.request = {"crcAddress":"", "federationName": "", "federateName": ""};
    console.log(JSON.stringify($scope.request));

    $scope.create = function() {
        $http({method: "POST", url: "/federates", data: JSON.stringify($scope.request)}).success(function(data) {
            console.log(data);
        });
    }

    $scope.delete = function() {
        $http({method: "Delete", url: "/federates", data: JSON.stringify($scope.request)}).success(function(data) {
           console.log(data);
        });
    }
}]);
