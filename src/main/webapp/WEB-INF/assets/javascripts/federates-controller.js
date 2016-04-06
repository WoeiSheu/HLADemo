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

    /**********************
     * Created by Hypocrisy.
     * Draw x Axis.
     **********************/
    var lineLength = 1000;
    var intervalLength = 10;
    //$('#xAxis').css('width',lineLength);
    var context = document.getElementById('xAxis').getContext("2d");
    context.beginPath();
    context.lineCap = "round";
    context.moveTo(0, 10);
    context.lineTo(lineLength, 10);
    context.lineWidth=2;
    for(var i = 0; i < lineLength; i+=intervalLength) {
        context.moveTo(i,10);
        context.lineTo(i,0);
    }
    context.strokeStyle = "#ff0000";
    context.fillStyle = "#ffcc00";
    context.fill();
    context.stroke();
    context.closePath();
}]);
