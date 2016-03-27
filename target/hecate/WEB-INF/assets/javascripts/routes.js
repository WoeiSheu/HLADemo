/**
 * Created by Gaea on 3/24/2016.
 */
angular.module('HLADemo').config(['$routeProvider', function($routeProvider){
    $routeProvider
        .when('/federates', {
            templateUrl: 'angularjs/federates.html',
            controller: 'FederatesController',
            controllerAs: 'federatesCtrl'
        })
        .when('/rti', {
            templateUrl: 'angularjs/rti.html',
            controller: 'RTIController',
            controllerAs: 'rtiCtrl'
        })
        .when('/administration', {
            templateUrl: 'angularjs/administration.html',
            controller: 'AdministrationController',
            controllerAs: 'adminCtrl'
        })
        .when('/', {
            redirectTo: '/federates'
        })
        .otherwise({
            redirectTo: '/'
        });
}]);