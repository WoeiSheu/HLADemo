/**
 * Created by Hypocrisy on 3/24/2016.
 */
angular.module('HLADemo').controller('RTIController', ['$http', '$scope', '$location', function($http, $scope, $location) {
    var ctrl = this;

    var drawRTI = function(x,y,width,height,text) {
        if(!x) {
            x = 0;
        }
        if(!y) {
            y = 0;
        }
        if(!width) {
            width = 200;
        }
        if(!height) {
            height = 80;
        }
        context.fillStyle='#646464';
        context.fillRect(x,y,width,height);
        context.fillStyle='#ffffff';
        context.font = '30px italic Consolas';
        context.fillText(text,x+(width-15*text.length)/2,y+height/2+8);
    };

    var equallyDivide = function(x,parentWidth,num,width) {
        if(!width || width*num > parentWidth) {            // notice if we define this above, we will get a global variable.
            var width = (parentWidth/num)*0.8;
        }
        var gap = parentWidth/num-width;

        var xArray = [];
        if(num % 2 == 0) {
            for(var i = num; i > 0; i--) {
                var tmp = x + parentWidth/2 + Math.pow(-1,i+1) * ( Math.floor(i/2)*(width+gap) ) + gap/2;
                xArray.push(tmp);
            }
        } else {
            for(var i = num; i > 0; i--) {
                var tmp = x + parentWidth/2 + Math.pow(-1,i) * ( Math.floor(i/2)*(width+gap) ) - width/2 ;
                xArray.push(tmp);
            }
        }
        xArray.sort();
        return xArray;
    };

    var canvasWidth = $('#rtiView').parent('div').width();
    var perRtiHeight = 300;
    $('#rtiView').attr('width',canvasWidth);
    var context = document.getElementById('rtiView').getContext('2d');

    $http({method: 'GET', url: '/federates'}).success(function(data) {
        //console.log(data);
        ctrl.federatesWithAttributes = data;
        var canvasHeight = perRtiHeight*Object.keys(data).length;
        $('#rtiView').attr('height', canvasHeight);

        var federationIndex = -1;
        for(var federation in ctrl.federatesWithAttributes) {
            federationIndex += 1;
            var federationWidth = canvasWidth*0.9;
            var federationHeight = 80;
            var x = (canvasWidth-federationWidth)/2;
            var y = federationIndex*perRtiHeight + (perRtiHeight-federationHeight)/2;
            drawRTI(x,y,federationWidth,federationHeight,federation);

            var num = Object.keys(ctrl.federatesWithAttributes[federation]).length;
            var bottomNum = Math.floor(num/2);
            var topNum = num - bottomNum;
            var federateWidth = 120;
            var federateHeight = 60;
            var topXArray = equallyDivide(x,federationWidth,topNum,federateWidth);
            var bottomXArray = equallyDivide(x,federationWidth,bottomNum,federateWidth);

            var federateIndex = -1;
            for(var federate in ctrl.federatesWithAttributes[federation]) {
                federateIndex += 1;

                var x,y;
                if(federateIndex < topNum) {
                    x = topXArray[federateIndex];
                    y = federationIndex*perRtiHeight+10;
                    // Draw line to connect federate with rti
                    context.moveTo(x+federateWidth/2,y+federateHeight);
                    context.lineTo(x+federateWidth/2,federationIndex*perRtiHeight + (perRtiHeight-federationHeight)/2);
                } else {
                    x = bottomXArray[federateIndex-topNum];
                    y = (federationIndex+1)*perRtiHeight-federateHeight-10;
                    // Draw line to connect federate with rti
                    context.moveTo(x+federateWidth/2,y);
                    context.lineTo(x+federateWidth/2,federationIndex*perRtiHeight + (perRtiHeight+federationHeight)/2);
                }
                // Draw line to connect federate with rti
                context.lineWidth = 2;
                context.strokeStyle = "orange";
                context.fillStyle = "orange";
                context.fill();
                context.stroke();

                drawRTI(x,y,federateWidth,federateHeight,federate);
            }
        }
    });
}]);
