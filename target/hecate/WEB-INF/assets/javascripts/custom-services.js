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
})
.factory('sortFederate', function() {
    return {
        compareFederate: function (propertyName1,propertyName2) {
            return function (object1, object2) {
                var value1 = object1[propertyName1];
                var value2 = object2[propertyName1];
                if (value2 < value1) {
                    return 1;
                }
                else if (value2 > value1) {
                    return -1;
                }
                else {
                    value1 = object1[propertyName2];
                    value2 = object2[propertyName2];
                    if(value2 < value1) {
                        return 1;
                    } else {
                        return -1;
                    }
                }
                return 0;
            };
        }
    }
});
