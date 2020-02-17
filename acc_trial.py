import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from scipy.fftpack import fftfreq, fft, ifft
import scipy.signal as sig
import smoothed_zscore as sz
from scipy.integrate import romb
from scipy.integrate import cumtrapz


def calculate_derivative_list(timestampList, toCalcList):
    if (timestampList is None or len(timestampList) == 0 or toCalcList is None 
        or len(toCalcList) == 0):
        ValueError(" calculate_derivative_list:  invalid parameters ")
    toCalc = np.asarray(toCalcList)
    resultList = []    
    resultList.append(0)
    for i in range(len(toCalc)):
        if i == len(toCalc)-1: 
            break
        resultList.append((toCalc[i + 1] - toCalc[i]) / (timestampList[i + 1] - timestampList[i]))
    return resultList

def apply_lowpass_filter(a, cutoff_freq):
    if (a is None or len(a) == 0 or cutoff_freq is None 
        or cutoff_freq<0):
        ValueError(" apply_lowpass_filter:  invalid parameters ")
    a[cutoff_freq:] = 0
    return a

# dictionary for the Grey-code extraction
grey_code_dict =	{
  '1': '01',
  '0': '00',
  '-1': '11'
}

# jump in terms of datapoint used for extracting the grey codedata_watch['x_acc_fin'][firstpeak_index:len(x_dy)]
jump = 2
# global variables used as parameters of Savitzky-Golay filers
window_length_savgol = 15
polyorder_savgol = 5
fc = 40



def grey_code_extraction_2bit(a, b):
    if (a is None or len(a) == 0 or b is None or len(b) == 0):
        ValueError(" grey_code_extraction:  invalid parameters ")
    i = 0
    bits_str = np.array([], dtype = str)
    while(i + jump < len(a) or i + jump < len(b)):        
        if (a[i + jump] - a[i] >= 0):
            app_str = '0'
            if (b[i + jump]- b[i] >= 0):
                app_str += '0'
            else:
                app_str += '1'
        else:
            app_str = '1'
            if (b[i + jump] - b[i] >= 0):
                app_str += '1'
            else:
                app_str += '0'
        bits_str = np.append(bits_str, app_str)
        i += 1
    return bits_str


vel_file_path = 'Test_Data/Real_Beginning/gravity_removal_api/Drawing_Data/2020-02-10_1_smartphone_sample.csv'
cmp_file_path = 'Test_Data/Real_Beginning/gravity_removal_api/Drawing_Data/2020-02-10_1_smartphone_sample.csv'
acc_file_path = 'Test_Data/Real_Beginning/gravity_removal_api/Accelerometer_Data/2020-02-10_1_watch_sample_copy.csv'


# DataFrame collection from files
data_phone = pd.read_csv(vel_file_path, engine='python')
data_cmp = pd.read_csv(cmp_file_path, engine='python')
data_watch = pd.read_csv(acc_file_path, engine='python')


#plt.plot(data_phone['x'], data_phone['y'])
#plt.figure()

#dx = 0.05
x_dy = calculate_derivative_list(data_phone['timestamp'], data_phone['x']) #np.diff(data_phone['x'])
#x_dy = np.append(x_dy, data_phone['x_velocity'][0])



y_dy = calculate_derivative_list(data_phone['timestamp'], data_phone['y']) #np.diff(data_phone['y'])
#y_dy = np.append(y_dy, data_phone['y_velocity'][0])



x_dy_fft = fft(x_dy)
x_dy_fft = apply_lowpass_filter(x_dy_fft,fc)
x_dy = np.real(ifft(x_dy_fft))
x_dy = sig.savgol_filter(x_dy, window_length_savgol, polyorder_savgol)

y_dy_fft = fft(y_dy)
y_dy_fft = apply_lowpass_filter(y_dy_fft,fc)
y_dy = np.real(ifft(y_dy_fft))
y_dy = sig.savgol_filter(y_dy, window_length_savgol, polyorder_savgol)




x_vel_dy = calculate_derivative_list(data_phone['timestamp'], x_dy) #np.diff(data_phone['x'])
#x_dy = np.append(x_dy, data_phone['x_velocity'][0])



y_vel_dy = calculate_derivative_list(data_phone['timestamp'], y_dy) #np.diff(data_phone['y'])
#y_dy = np.append(y_dy, data_phone['y_velocity'][0])



x_dy_fft = fft(x_vel_dy)
x_dy_fft = apply_lowpass_filter(x_dy_fft,fc)
x_vel_dy = np.real(ifft(x_dy_fft))
x_vel_dy = sig.savgol_filter(x_vel_dy, window_length_savgol, polyorder_savgol)

y_dy_fft = fft(y_vel_dy)
y_dy_fft = apply_lowpass_filter(y_dy_fft,fc)
y_vel_dy = np.real(ifft(y_dy_fft))
y_vel_dy = sig.savgol_filter(y_vel_dy, window_length_savgol, polyorder_savgol)


x_fft = fft(data_cmp['x_velocity'])
x_fft = apply_lowpass_filter(x_fft,fc)
x = np.real(ifft(x_fft))
data_cmp['x_velocity'] = sig.savgol_filter(x, window_length_savgol, polyorder_savgol)

y_fft = fft(data_cmp['y_velocity'])
y_fft = apply_lowpass_filter(y_fft,fc)
y = np.real(ifft(y_fft))
data_cmp['y_velocity'] = sig.savgol_filter(y, window_length_savgol, polyorder_savgol)



x_fft = fft(data_watch['x_acc'])
x_fft = apply_lowpass_filter(x_fft,fc)
x = np.real(ifft(x_fft))
data_watch['x_acc'] = sig.savgol_filter(x, window_length_savgol, polyorder_savgol)



y_fft = fft(data_watch['y_acc'])
y_fft = apply_lowpass_filter(y_fft,fc)
y = np.real(ifft(y_fft))
data_watch['y_acc'] = sig.savgol_filter(y, window_length_savgol, polyorder_savgol)


plt.plot(data_watch['x_acc'], data_watch['y_acc'])
plt.figure()
plt.plot(x_vel_dy, y_vel_dy)
#plt.plot(data_phone['timestamp'], x_dy)

#plt.figure()
#plt.plot(x_dy, y_dy)

x_acc = sig.resample(data_watch['x_acc'], len(x_vel_dy))
y_acc = sig.resample(data_watch['y_acc'], len(y_vel_dy))


watch_acc_greycode_2bit = grey_code_extraction_2bit(x_acc, y_acc)


phone_acc_greycode_2bit = grey_code_extraction_2bit(x_vel_dy, y_vel_dy)
match = 0

for i in range(0, len(phone_acc_greycode_2bit)):
    #print(watch_acc_greycode_2bit[i], phone_acc_greycode_2bit[i])
    if watch_acc_greycode_2bit[i] == phone_acc_greycode_2bit[i]:
        match += 1

print(match / len(watch_acc_greycode_2bit))

#plt.plot(range(0, len(data_watch['z_acc'][47:])), data_watch['z_acc'][47:])
#plt.figure()
#plt.plot(range(0, len(watch_linear_x_acc_lp_resampled)), watch_linear_x_acc_lp_resampled)
#plt.figure()
#plt.plot(range(0, len(x_dy)), x_dy)
#print(data_watch['timestamp'][0], data_phone['timestamp'][31])
#plt.figure()
#plt.plot(range(0, len(data_watch['x_acc_fin'])), data_watch['x_acc_fin'])
plt.show()