/**
 * Created by Hypocrisy on 3/24/2016.
 * This controller displays the advanced process of federates.
 */
angular.module('HLADemo').controller('FederatesController', ['$http', '$scope', '$interval', function($http, $scope, $interval) {
    var ctrl = this;

    //$interval(fn, delay, [count], [invokeApply], [Pass]);
    //https://code.angularjs.org/1.5.0/docs/api/ng/service/$interval
    var intervalPromise = $interval(function () {
        $http({method: 'GET', url: '/federates'}).success(function(data) {
            //console.log(data);
            $scope.federatesWithAttributes = data;
        });
    }, 1000);
    $scope.$on('$destroy',function() {
        if(intervalPromise) {
            $interval.cancel(intervalPromise);
        }
    });

    /**********************
     * Created by Hypocrisy.
     * Draw x Axis.
     **********************/
    var drawXAxis = function(canvasID,offset, intervalLength) {
        var lineLength = $('#' + canvasID).parent("div").width();
        $('#' + canvasID).attr('width',lineLength);
        $('#' + canvasID).attr('height',36);
        var context = document.getElementById(canvasID).getContext("2d");
        context.beginPath();
        //context.lineCap = "round";
        context.moveTo(offset, 10);
        context.lineTo(lineLength, 10);
        context.lineWidth=2;
        for(var i = offset; i < lineLength; i+=intervalLength) {
            context.moveTo(i+context.lineWidth/2,10);     // why +lineWidth/2? because we will draw only lineWidth/2 for the first line.
            context.lineTo(i+context.lineWidth/2,5);
        }
        for(var i = offset; i < lineLength; i+=intervalLength*5) {
            context.moveTo(i+context.lineWidth/2,5);
            context.lineTo(i+context.lineWidth/2,0);
        }
        context.strokeStyle = "#ff0000";
        context.fillStyle = "#ffcc00";
        context.fill();
        context.stroke();
        context.closePath();

        context.fillStyle = "#000000";
        context.font = "20px italic";
        //var coordinate = Math.floor((lineLength-80)/(5*intervalLength)) * (5*intervalLength);
        for(var i = 0; i < lineLength-80; i+=intervalLength*5) {        // 80px is 4*20px, 20px is font-size
            context.fillText(i.toString(), i+offset-10, 30);
        }
    }
    $scope.offset = 10;
    drawXAxis("xAxis", $scope.offset, 20);
}]);
