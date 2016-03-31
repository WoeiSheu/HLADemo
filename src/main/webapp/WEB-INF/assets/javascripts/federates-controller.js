/**
 * Created by Hypocrisy on 3/24/2016.
 * This controller displays the advanced process of federates.
 */
angular.module('HLADemo').controller('FederatesController', ['$http', '$scope', '$interval', function($http, $scope, $interval) {
    var ctrl = this;

    //$interval(fn, delay, [count], [invokeApply], [Pass]);
    //https://code.angularjs.org/1.5.0/docs/api/ng/service/$interval
    $interval(function () {
        $http({method: 'GET', url: '/federates'}).success(function(data) {
            //console.log(data);
            $scope.federatesWithTime = data;
        });
    }, 1000);
}]);
