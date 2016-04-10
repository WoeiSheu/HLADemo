/**
 * Created by Hypocrisy on 4/9/2016.
 * This file includes all the angular services that I created.
 */
angular.module('HLADemo')
.factory('passDataService', function () {
    var storage = {};
    return {
        saveData: function (data) {
            storage = data;
        },
        getData: function () {
            return storage;
        }
    };
});
