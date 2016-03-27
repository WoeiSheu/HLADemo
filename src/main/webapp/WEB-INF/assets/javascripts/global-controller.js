/**
 * Created by Gaea on 3/27/2016.
 */
angular.module('HLADemo').controller('GlobalController', ['$http', '$scope', '$cookies', function($http, $scope, $cookies) {
    var ctrl = this;
    //$cookies.put( 'id', Date.now().toString() );
    sessionStorage.setItem('id',Date.now().toString());
}]);
