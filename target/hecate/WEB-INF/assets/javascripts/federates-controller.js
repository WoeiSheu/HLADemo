/**
 * Created by Hypocrisy on 3/24/2016.
 */
angular.module('HLADemo').controller('FederatesController', ['$http', '$scope', '$interval', function($http, $scope, $interval) {
    var ctrl = this;

    //$interval(fn, delay, [count], [invokeApply], [Pass]);
    //https://code.angularjs.org/1.5.0/docs/api/ng/service/$interval
    var leftPos = "0px";
    $interval(function () {
        /*
        (function test(step) {
            $("#tank").css('left', leftPos);
            leftPos = (parseInt(leftPos.substr(0, leftPos.length - 2)) + step) + 'px';
        })(60);
        */

        $http({method: 'GET', url: '/federates/time/' + sessionStorage.getItem('id')}).success(function(data) {
            //console.log(data);
            leftPos = parseInt(data.time*10) + 'px';
            $("#tank").css('left',leftPos);
        });
    }, 1000);

}]);
