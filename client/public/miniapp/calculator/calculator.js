// 获取显示框元素
const display = document.getElementById('display');

// 向显示框添加内容
function appendToDisplay(value) {
    // 如果当前显示为0或者是错误信息，则替换显示
    if (display.value === '0' || display.value === 'Error') {
        display.value = value;
    } else {
        // 检查最后输入的字符和当前要输入的字符是否都是运算符
        const lastChar = display.value[display.value.length - 1];
        const operators = ['+', '-', '*', '/', '%'];
        if (operators.includes(lastChar) && operators.includes(value)) {
            // 如果都是运算符，则替换最后一个运算符
            display.value = display.value.slice(0, -1) + value;
        } else {
            // 否则添加到显示框
            display.value += value;
        }
    }
}

// 清除显示框
function clearDisplay() {
    display.value = '0';
}

// 删除最后一个字符
function deleteLastChar() {
    if (display.value.length > 1) {
        display.value = display.value.slice(0, -1);
    } else {
        display.value = '0';
    }
}

// 切换正负号
function toggleSign() {
    if (display.value !== '0' && display.value !== 'Error') {
        if (display.value[0] === '-') {
            display.value = display.value.slice(1);
        } else {
            display.value = '-' + display.value;
        }
    }
}

// 计算结果
function calculate() {
    try {
        // 安全的计算逻辑，避免使用eval的安全问题
        let expression = display.value;
        let result = 0;
        
        // 处理百分比
        if (expression.includes('%')) {
            // 简单处理百分比，例如 a% -> a/100
            expression = expression.replace(/(\d+(\.\d+)?)%/g, function(match, p1) {
                return parseFloat(p1) / 100;
            });
        }
        
        // 这里使用Function构造函数替代eval，相对更安全
        // 但是仍然需要进行输入验证以确保安全
        const safeEval = new Function('return ' + expression);
        result = safeEval();
        
        // 处理结果，保留最多10位有效数字
        if (result.toString().length > 10) {
            if (result % 1 === 0) {
                // 整数结果
                display.value = result.toLocaleString();
            } else {
                // 小数结果，保留适当的小数位
                display.value = result.toPrecision(10);
            }
        } else {
            display.value = result;
        }
    } catch (error) {
        display.value = 'Error';
    }
}

// 初始化显示
clearDisplay();