package com.qianfeng.smsplatform.search.service.Impl;

import com.qianfeng.smsplatform.common.model.Standard_Submit;
import com.qianfeng.smsplatform.search.service.FilterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.qianfeng.smsplatform.common.constants.StrategyConstants.STRATEGY_ERROR_BLACK;

/*
//                            _ooOoo_  
//                           o8888888o  
//                           88" . "88  
//                           (| -_- |)  
//                            O\ = /O  
//                        ____/`---'\____  
//                      .   ' \\| |// `.  
//                       / \\||| : |||// \  
//                     / _||||| -:- |||||- \  
//                       | | \\\ - /// | |  
//                     | \_| ''\---/'' | |  
//                      \ .-\__ `-` ___/-. /  
//                   ___`. .' /--.--\ `. . __  
//                ."" '< `.___\_<|>_/___.' >'"".  
//               | | : `- \`.;`\ _ /`;.`/ - ` : | |  
//                 \ \ `-. \_ __\ /__ _/ .-` / /  
//         ======`-.____`-.___\_____/___.-`____.-'======  
//                            `=---='  
//  
//         .............................................  
//                  佛祖镇楼                  BUG辟易  
//          佛曰:  
//                  写字楼里写字间，写字间里程序员；  
//                  程序人员写程序，又拿程序换酒钱。  
//                  酒醒只在网上坐，酒醉还来网下眠；  
//                  酒醉酒醒日复日，网上网下年复年。  
//                  但愿老死电脑间，不愿鞠躬老板前；  
//                  奔驰宝马贵者趣，公交自行程序员。  
//                  别人笑我忒疯癫，我笑自己命太贱；  


*裴少泊的修仙之路
*描述：
*/
@Service("BlackFilter")
public class BlackFilterService implements FilterService {
    private String[] str={"123123","!23123123","12123123123"};

    @Override
    public Standard_Submit filtrate(Standard_Submit message) {
        String destMobile = message.getDestMobile();
        for(int i=0;i<str.length;i++) {
            if (destMobile.equals(str[i])) {
                message.setErrorCode(STRATEGY_ERROR_BLACK);
                return message;
            }
        }
        return message;
    }
}
